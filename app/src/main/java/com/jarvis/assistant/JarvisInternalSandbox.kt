package com.jarvis.assistant

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import kotlin.math.pow

enum class SandboxOperation {
    KNOWLEDGE_SEARCH, MATH_EVAL, APPS_LIST, MEMORY_STATUS, SYSTEM_STATUS,
    PLAN_OPEN, PLAN_WEB, BLOCKED, UNKNOWN
}

data class ParsedSandboxCommand(val operation: SandboxOperation, val argument: String = "")

data class JarvisSandboxResult(
    val status: String,
    val command: String,
    val output: String,
    val requiresUserConfirmation: Boolean = false,
    val sideEffectsPerformed: Boolean = false
)

object JarvisInternalSandbox {
    fun answerQuestion(context: Context, question: String): String? =
        JarvisKnowledgeBase.answer(context.applicationContext, question)

    fun parseCommand(raw: String): ParsedSandboxCommand {
        val command = raw.trim()
        if (command.isBlank() || command.length > 512) {
            return ParsedSandboxCommand(SandboxOperation.BLOCKED, "A entrada está vazia ou excede 512 caracteres.")
        }
        val normalized = JarvisKnowledgeIndex.normalize(command)
        if (Regex("^(?:shell|exec|execute|process|runtime|bash|sh|su|root|rm|chmod|chown|curl|wget|cat|dd|mount|am|pm)\\b")
                .containsMatchIn(normalized) ||
            normalized.contains(".. /") || normalized.contains("/data/data/") ||
            normalized.contains("service role") || normalized.contains("private key")) {
            return ParsedSandboxCommand(SandboxOperation.BLOCKED, "A sandbox não executa shell arbitrário nem aceita acesso direto a arquivos ou credenciais.")
        }
        return when {
            command.startsWith("knowledge.search ", true) ->
                ParsedSandboxCommand(SandboxOperation.KNOWLEDGE_SEARCH, command.substringAfter(' ').trim())
            command.startsWith("math.eval ", true) ->
                ParsedSandboxCommand(SandboxOperation.MATH_EVAL, command.substringAfter(' ').trim())
            normalized == "apps list" -> ParsedSandboxCommand(SandboxOperation.APPS_LIST)
            normalized == "memory status" -> ParsedSandboxCommand(SandboxOperation.MEMORY_STATUS)
            normalized == "system status" -> ParsedSandboxCommand(SandboxOperation.SYSTEM_STATUS)
            command.startsWith("plan.open ", true) ->
                ParsedSandboxCommand(SandboxOperation.PLAN_OPEN, command.substringAfter(' ').trim())
            command.startsWith("plan.web ", true) ->
                ParsedSandboxCommand(SandboxOperation.PLAN_WEB, command.substringAfter(' ').trim())
            else -> ParsedSandboxCommand(SandboxOperation.UNKNOWN, "Comando fora da lista permitida.")
        }
    }

    fun runReadOnly(context: Context, raw: String): JarvisSandboxResult {
        val parsed = parseCommand(raw)
        return when (parsed.operation) {
            SandboxOperation.KNOWLEDGE_SEARCH -> {
                val hit = JarvisKnowledgeBase.lookup(context, parsed.argument)
                if (hit == null) result("NOT_FOUND", "knowledge.search", "Nenhum item local com confiança suficiente.")
                else result("OK", "knowledge.search", "[" + hit.category + "] " + hit.answer)
            }
            SandboxOperation.MATH_EVAL -> {
                val value = SafeSandboxMath.evaluate(parsed.argument)
                if (value == null) result("BLOCKED", "math.eval", "Expressão inválida, grande demais ou fora dos operadores permitidos.")
                else result("OK", "math.eval", "Resultado: " + value)
            }
            SandboxOperation.APPS_LIST -> {
                val apps = launchableApps(context)
                result("OK", "apps.list", apps.take(80).joinToString("\n").ifBlank { "Nenhum aplicativo lançável encontrado." })
            }
            SandboxOperation.MEMORY_STATUS -> result(
                "OK", "memory.status",
                "Macros locais: " + JarvisMemory.count(context) + "; fatos offline: " + JarvisKnowledgeBase.count(context) + "."
            )
            SandboxOperation.SYSTEM_STATUS -> result(
                "OK", "system.status",
                "Android API " + Build.VERSION.SDK_INT + "; pacote " + context.packageName + "; sandbox somente-leitura por padrão."
            )
            SandboxOperation.PLAN_OPEN -> {
                val label = JarvisCommandRouter.inspectLaunchableApp(context, parsed.argument)
                if (label == null) result("NOT_FOUND", "plan.open", "O Android não encontrou um aplicativo lançável com esse nome.")
                else JarvisSandboxResult(
                    status = "NEEDS_CONFIRMATION",
                    command = "plan.open",
                    output = "Plano criado para abrir " + label + ". Nenhum aplicativo foi aberto.",
                    requiresUserConfirmation = true,
                    sideEffectsPerformed = false
                )
            }
            SandboxOperation.PLAN_WEB -> {
                val query = parsed.argument.trim()
                if (query.isBlank()) result("BLOCKED", "plan.web", "A consulta está vazia.")
                else JarvisSandboxResult(
                    status = "NEEDS_CONFIRMATION",
                    command = "plan.web",
                    output = "Plano criado para pesquisar: " + query.take(180) + ". Nenhuma consulta foi enviada.",
                    requiresUserConfirmation = true,
                    sideEffectsPerformed = false
                )
            }
            SandboxOperation.BLOCKED -> result("BLOCKED", "policy", parsed.argument)
            SandboxOperation.UNKNOWN -> result("UNKNOWN", "parser", parsed.argument)
        }
    }

    private fun launchableApps(context: Context): List<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return try {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                .asSequence()
                .filter { it.activityInfo.packageName != context.packageName }
                .map { it.loadLabel(context.packageManager).toString() }
                .filter { it.isNotBlank() }
                .distinct()
                .sortedBy { it.lowercase() }
                .toList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun result(status: String, command: String, output: String) =
        JarvisSandboxResult(status, command, output, false, false)
}

private object SafeSandboxMath {
    fun evaluate(raw: String): String? {
        val input = raw.trim()
        if (input.isBlank() || input.length > 120 || !input.matches(Regex("[0-9+*/^().\\s-]+"))) return null
        return try {
            val value = Parser(input).eval()
            if (!value.isFinite() || kotlin.math.abs(value) > 1.0e100) null
            else if (value == value.toLong().toDouble()) value.toLong().toString()
            else value.toString()
        } catch (_: Exception) {
            null
        }
    }

    private class Parser(private val input: String) {
        private var index = 0
        private var depth = 0

        fun eval(): Double {
            val value = expression()
            spaces()
            require(index == input.length)
            return value
        }

        private fun expression(): Double {
            var value = term()
            while (true) {
                spaces()
                value = when {
                    take('+') -> value + term()
                    take('-') -> value - term()
                    else -> return value
                }
                require(value.isFinite() && kotlin.math.abs(value) <= 1.0e100)
            }
        }

        private fun term(): Double {
            var value = power()
            while (true) {
                spaces()
                value = when {
                    take('*') -> value * power()
                    take('/') -> {
                        val divisor = power()
                        require(divisor != 0.0)
                        value / divisor
                    }
                    else -> return value
                }
                require(value.isFinite() && kotlin.math.abs(value) <= 1.0e100)
            }
        }

        private fun power(): Double {
            var value = unary()
            spaces()
            if (take('^')) {
                value = value.powChecked(power())
                require(value.isFinite() && kotlin.math.abs(value) <= 1.0e100)
            }
            return value
        }

        private fun unary(): Double {
            spaces()
            return when {
                take('+') -> unary()
                take('-') -> -unary()
                take('(') -> {
                    depth++
                    require(depth <= 24)
                    val value = expression()
                    spaces()
                    require(take(')'))
                    depth--
                    value
                }
                else -> number()
            }
        }

        private fun number(): Double {
            spaces()
            val start = index
            var dots = 0
            while (index < input.length && (input[index].isDigit() || input[index] == '.')) {
                if (input[index] == '.') dots++
                require(dots <= 1)
                index++
            }
            require(index > start)
            return input.substring(start, index).toDouble()
        }

        private fun spaces() {
            while (index < input.length && input[index].isWhitespace()) index++
        }

        private fun take(char: Char): Boolean {
            if (index < input.length && input[index] == char) {
                index++
                return true
            }
            return false
        }

        private fun Double.powChecked(exponent: Double): Double {
            require(kotlin.math.abs(exponent) <= 1000.0)
            return this.pow(exponent)
        }
    }
}
