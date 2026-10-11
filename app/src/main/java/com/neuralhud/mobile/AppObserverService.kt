package com.neuralhud.mobile

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AppObserverService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var activeTargetPackage = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        LocalNeuralModel(this)
        LocalAutomationEngine.setStatus(this, "IA local pronta. Salve uma instrução no HUD para ativá-la.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString().orEmpty()
        if (packageName.isBlank() || packageName == this.packageName || isSystemOrLauncher(packageName)) return

        getSharedPreferences("neural_hud_observer", MODE_PRIVATE).edit()
            .putString("last_package", packageName)
            .putLong("last_observed_at", System.currentTimeMillis())
            .apply()

        val instruction = LocalAutomationEngine.onAppOpened(this, packageName) ?: return
        activeTargetPackage = packageName
        LocalAutomationEngine.setStatus(this, "Detectei o app $packageName. Interpretando a instrução local…")
        handler.postDelayed({
            if (activeTargetPackage == packageName) executeStep(instruction, packageName, 0)
        }, 650L)
    }

    private fun isSystemOrLauncher(packageName: String): Boolean {
        if (packageName == "android" || packageName.startsWith("com.android.systemui") ||
            packageName.startsWith("com.android.settings")) return true
        return try {
            val home = packageManager.resolveActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
            )?.activityInfo?.packageName
            packageName == home
        } catch (_: Exception) {
            false
        }
    }

    private fun executeStep(instruction: ParsedInstruction, packageName: String, index: Int) {
        if (index >= instruction.actions.size) {
            LocalAutomationEngine.setStatus(this, "Concluído em $packageName: ${instruction.actionText}")
            return
        }
        val action = instruction.actions[index]
        when (action.type) {
            LocalActionType.WAIT -> {
                val wait = action.argument.toLongOrNull()?.coerceIn(100L, 3000L) ?: 600L
                handler.postDelayed({ executeStep(instruction, packageName, index + 1) }, wait)
            }
            LocalActionType.BACK -> {
                val success = performGlobalAction(GLOBAL_ACTION_BACK)
                continueOrStop(instruction, packageName, index, success, if (success) "Voltei uma tela." else "O Android não permitiu voltar.")
            }
            LocalActionType.HOME -> {
                val success = performGlobalAction(GLOBAL_ACTION_HOME)
                continueOrStop(instruction, packageName, index, success, if (success) "Voltei à tela inicial." else "O Android não permitiu ir à tela inicial.")
            }
            LocalActionType.ENTER -> {
                val root = rootForPackage(packageName)
                val field = root?.let { findEditable(it) }
                val success = field?.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id) == true ||
                    root?.let { clickFirstLabel(it, listOf("pesquisar", "search", "enviar", "send", "ir", "go")) } == true
                continueOrStop(instruction, packageName, index, success, if (success) "Enviei/executei Enter." else "Não encontrei um campo ou botão para Enter.")
            }
            LocalActionType.TYPE_TEXT -> {
                val root = rootForPackage(packageName)
                val field = root?.let { findEditable(it) }
                val success = field?.let { setNodeText(it, action.argument) } == true
                continueOrStop(instruction, packageName, index, success, if (success) "Texto inserido no campo disponível." else "Não encontrei um campo de texto editável (campos de senha são ignorados).")
            }
            LocalActionType.CLICK_TEXT -> {
                val root = rootForPackage(packageName)
                val success = root?.let { clickText(it, action.argument) } == true
                continueOrStop(instruction, packageName, index, success, if (success) "Toque executado em “${action.argument}”." else "Não encontrei um controle clicável com o texto “${action.argument}”.")
            }
            LocalActionType.SCROLL_DOWN, LocalActionType.SCROLL_UP -> {
                val root = rootForPackage(packageName)
                val direction = if (action.type == LocalActionType.SCROLL_DOWN) {
                    AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                } else {
                    AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                }
                val success = root?.let { scrollNode(it, direction) } == true
                continueOrStop(instruction, packageName, index, success, if (success) "Rolagem executada." else "Não encontrei uma área rolável.")
            }
            LocalActionType.SEARCH -> executeSearch(instruction, packageName, index, action.argument)
            LocalActionType.OPEN_APP -> {
                val opened = launchApp(action.argument)
                LocalAutomationEngine.setStatus(
                    this,
                    if (opened) "Abri o app “${action.argument}”." else "Não encontrei um app chamado “${action.argument}”."
                )
            }
        }
    }

    private fun executeSearch(instruction: ParsedInstruction, packageName: String, index: Int, query: String) {
        val root = rootForPackage(packageName)
        val existingField = root?.let { findEditable(it) }
        if (existingField != null && setNodeText(existingField, query)) {
            val submitted = existingField.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
            if (!submitted) root?.let { clickFirstLabel(it, listOf("pesquisar", "search", "buscar", "go", "ir")) }
            LocalAutomationEngine.setStatus(this, "Pesquisa “$query” inserida no app.")
            handler.postDelayed({ executeStep(instruction, packageName, index + 1) }, 600L)
            return
        }

        val clickedSearch = root?.let {
            clickFirstLabel(it, listOf("pesquisar", "search", "buscar", "procurar", "pesquisa", "magnifying glass"))
        } == true
        if (clickedSearch) {
            handler.postDelayed({
                val nextRoot = rootForPackage(packageName)
                val field = nextRoot?.let { findEditable(it) }
                if (field != null && setNodeText(field, query)) {
                    val submitted = field.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
                    if (!submitted) nextRoot?.let { clickFirstLabel(it, listOf("pesquisar", "search", "buscar", "go", "ir")) }
                    LocalAutomationEngine.setStatus(this, "Pesquisa “$query” inserida no app.")
                    handler.postDelayed({ executeStep(instruction, packageName, index + 1) }, 650L)
                } else {
                    launchWebSearch(query)
                }
            }, 700L)
        } else {
            launchWebSearch(query)
        }
    }

    private fun launchWebSearch(query: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query)))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            LocalAutomationEngine.setStatus(this, "O app não expôs uma busca acessível. Abri a pesquisa Google para “$query”.")
        } catch (_: Exception) {
            LocalAutomationEngine.setStatus(this, "Não consegui abrir a pesquisa “$query”.")
        }
    }

    private fun launchApp(label: String): Boolean {
        val apps = try {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                packageManager.getInstalledApplications(android.content.pm.PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getInstalledApplications(0)
            }
        } catch (_: Exception) {
            return false
        }
        val target = LocalAutomationEngine.normalize(label)
        val match = apps.firstOrNull { info ->
            info.packageName != packageName &&
                LocalAutomationEngine.normalize(packageManager.getApplicationLabel(info).toString()).contains(target) &&
                packageManager.getLaunchIntentForPackage(info.packageName) != null
        } ?: return false
        return try {
            val launchIntent = packageManager.getLaunchIntentForPackage(match.packageName) ?: return false
            startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun continueOrStop(
        instruction: ParsedInstruction,
        packageName: String,
        index: Int,
        success: Boolean,
        message: String
    ) {
        LocalAutomationEngine.setStatus(this, message)
        if (success) handler.postDelayed({ executeStep(instruction, packageName, index + 1) }, 450L)
    }

    private fun rootForPackage(expectedPackage: String): AccessibilityNodeInfo? {
        val current = rootInActiveWindow
        if (current?.packageName?.toString() == expectedPackage) return current
        return try {
            windows.firstOrNull { it.root?.packageName?.toString() == expectedPackage }?.root
        } catch (_: Exception) {
            null
        }
    }

    private fun findEditable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        collectNodes(root, nodes)
        return nodes.firstOrNull { it.isEditable && it.isEnabled && !it.isPassword && it.isVisibleToUser && it.isFocused }
            ?: nodes.firstOrNull { it.isEditable && it.isEnabled && !it.isPassword && it.isVisibleToUser }
    }

    private fun setNodeText(node: AccessibilityNodeInfo, text: String): Boolean {
        if (node.isPassword || !node.isEnabled) return false
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    private fun clickText(root: AccessibilityNodeInfo, label: String): Boolean {
        val target = LocalAutomationEngine.normalize(label)
        if (target.isBlank()) return false
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        collectNodes(root, nodes)
        val candidate = nodes.firstOrNull { node ->
            if (node.isPassword || !node.isVisibleToUser) false
            else {
                val text = LocalAutomationEngine.normalize(node.text?.toString().orEmpty())
                val desc = LocalAutomationEngine.normalize(node.contentDescription?.toString().orEmpty())
                text == target || desc == target || text.contains(target) || desc.contains(target)
            }
        } ?: return false
        return clickSelfOrParent(candidate)
    }

    private fun clickFirstLabel(root: AccessibilityNodeInfo, labels: List<String>): Boolean {
        for (label in labels) if (clickText(root, label)) return true
        return false
    }

    private fun clickSelfOrParent(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        repeat(6) {
            val item = current ?: return false
            if (item.isClickable && item.isEnabled && item.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            current = item.parent
        }
        return false
    }

    private fun scrollNode(root: AccessibilityNodeInfo, action: Int): Boolean {
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        collectNodes(root, nodes)
        val target = nodes.firstOrNull { it.isScrollable && it.isEnabled } ?: return false
        return target.performAction(action)
    }

    private fun collectNodes(node: AccessibilityNodeInfo?, output: MutableList<AccessibilityNodeInfo>) {
        if (node == null || output.size >= 350) return
        output.add(node)
        for (index in 0 until node.childCount) {
            if (output.size >= 350) break
            collectNodes(node.getChild(index), output)
        }
    }

    override fun onInterrupt() {
        activeTargetPackage = ""
    }
}
