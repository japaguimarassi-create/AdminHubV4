package com.jarvis.assistant

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.Intent
import android.os.Build
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AppObserverService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var activeTargetPackage = ""
    private var jarvisReceiverRegistered = false

    private val jarvisReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_EXECUTE_NOW -> {
                    val raw = intent.getStringExtra(EXTRA_INSTRUCTION).orEmpty()
                    val instruction = LocalAutomationEngine.parse(raw)
                    if (instruction == null) {
                        LocalAutomationEngine.setStatus(this@AppObserverService, "Não reconheci uma ação executável nesse comando.")
                        return
                    }
                    val target = activeTargetPackage.ifBlank {
                        rootInActiveWindow?.packageName?.toString().orEmpty()
                    }
                    if (target.isBlank() || target == packageName || isSystemOrLauncher(target)) {
                        LocalAutomationEngine.setStatus(this@AppObserverService, "Abra primeiro o aplicativo no qual deseja executar o comando.")
                        return
                    }
                    LocalAutomationEngine.setStatus(this@AppObserverService, "J.A.R.V.I.S.: executando comando em $target.")
                    handler.post { executeStep(instruction, target, 0) }
                }
                ACTION_GLOBAL_COMMAND -> {
                    val name = intent.getStringExtra(EXTRA_GLOBAL_ACTION).orEmpty()
                    val action = when (name) {
                        "back" -> GLOBAL_ACTION_BACK
                        "home" -> GLOBAL_ACTION_HOME
                        "notifications" -> GLOBAL_ACTION_NOTIFICATIONS
                        "quick_settings" -> GLOBAL_ACTION_QUICK_SETTINGS
                        "recents" -> GLOBAL_ACTION_RECENTS
                        "screenshot" -> if (Build.VERSION.SDK_INT >= 28) GLOBAL_ACTION_TAKE_SCREENSHOT else -1
                        else -> -1
                    }
                    val ok = action != -1 && performGlobalAction(action)
                    LocalAutomationEngine.setStatus(
                        this@AppObserverService,
                        if (ok) "J.A.R.V.I.S.: ação global executada ($name)." else "O Android não permitiu a ação global ($name)."
                    )
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        LocalAutomationEngine.setStatus(this, "Núcleo J.A.R.V.I.S. conectado. Comandos locais prontos.")
        registerJarvisReceiver()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString().orEmpty()
        if (packageName.isBlank() || packageName == this.packageName || isSystemOrLauncher(packageName)) return

        activeTargetPackage = packageName
        getSharedPreferences("neural_hud_observer", MODE_PRIVATE).edit()
            .putString("last_package", packageName)
            .putLong("last_observed_at", System.currentTimeMillis())
            .apply()

        val instruction = LocalAutomationEngine.onAppOpened(this, packageName) ?: return
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
        if (activeTargetPackage != packageName) {
            LocalAutomationEngine.setStatus(this, "Automação interrompida porque você mudou de aplicativo.")
            return
        }
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
                val success = field?.let { submitTextAction(it) } == true ||
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
                val previousPackage = packageName
                val targetPackage = resolveLaunchablePackage(action.argument)
                val opened = targetPackage != null && launchPackage(targetPackage)
                if (!opened) {
                    LocalAutomationEngine.setStatus(this, "Não consegui abrir o app “${action.argument}”.")
                    return
                }
                LocalAutomationEngine.setStatus(this, "J.A.R.V.I.S.: abri “${action.argument}”. Aguardando a próxima tela…")
                waitForAppTransition(instruction, previousPackage, index + 1, 0)
            }
        }
    }

    private fun executeSearch(instruction: ParsedInstruction, packageName: String, index: Int, query: String) {
        val root = rootForPackage(packageName)
        val existingField = root?.let { findSearchableField(it) }
        if (existingField != null && setNodeText(existingField, query)) {
            val submitted = submitTextAction(existingField)
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
                val field = nextRoot?.let { findSearchableField(it) }
                if (field != null && setNodeText(field, query)) {
                    val submitted = submitTextAction(field)
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

    private fun resolveLaunchablePackage(label: String): String? {
        val target = LocalAutomationEngine.normalize(label.trim())
        if (target.isBlank()) return null
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return try {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(launcherIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
                .asSequence()
                .map { it.activityInfo.packageName to it.loadLabel(packageManager).toString() }
                .filter { it.first != packageName }
                .distinctBy { it.first }
                .sortedBy { (pkg, appLabel) ->
                    when {
                        LocalAutomationEngine.normalize(appLabel) == target -> 0
                        LocalAutomationEngine.normalize(appLabel).startsWith(target) -> 1
                        LocalAutomationEngine.normalize(appLabel).contains(target) -> 2
                        pkg.contains(target) -> 3
                        else -> 9
                    }
                }
                .firstOrNull { (pkg, appLabel) ->
                    LocalAutomationEngine.normalize(appLabel) == target ||
                        LocalAutomationEngine.normalize(appLabel).startsWith(target) ||
                        LocalAutomationEngine.normalize(appLabel).contains(target) ||
                        pkg.contains(target)
                }?.first
        } catch (_: Exception) {
            null
        }
    }

    private fun launchApp(label: String): Boolean {
        val packageName = resolveLaunchablePackage(label) ?: return false
        return launchPackage(packageName)
    }

    private fun launchPackage(targetPackage: String): Boolean {
        return try {
            val intent = packageManager.getLaunchIntentForPackage(targetPackage) ?: return false
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun waitForAppTransition(
        instruction: ParsedInstruction,
        previousPackage: String,
        nextIndex: Int,
        attempt: Int
    ) {
        handler.postDelayed({
            val current = activeTargetPackage.ifBlank { rootInActiveWindow?.packageName?.toString().orEmpty() }
            if (current.isNotBlank() && current != previousPackage &&
                current != packageName && !isSystemOrLauncher(current)) {
                executeStep(instruction, current, nextIndex)
            } else if (attempt < 12) {
                waitForAppTransition(instruction, previousPackage, nextIndex, attempt + 1)
            } else {
                LocalAutomationEngine.setStatus(
                    this,
                    "O app abriu, mas não consegui confirmar a próxima tela. Continue manualmente ou divida o comando."
                )
            }
        }, 250L)
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

    private fun findSearchableField(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        collectNodes(root, nodes)
        val candidates = nodes.filter { it.isEditable && it.isEnabled && !it.isPassword && it.isVisibleToUser }
        return candidates.firstOrNull {
            val label = LocalAutomationEngine.normalize(
                "${it.hintText ?: ""} ${it.contentDescription ?: ""} ${it.viewIdResourceName ?: ""}"
            )
            listOf("pesquisar", "buscar", "search", "query", "procurar").any { token -> token in label }
        } ?: candidates.firstOrNull { it.isFocused }
    }

    private fun setNodeText(node: AccessibilityNodeInfo, text: String): Boolean {
        if (node.isPassword || !node.isEnabled) return false
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    private fun submitTextAction(node: AccessibilityNodeInfo): Boolean {
        if (Build.VERSION.SDK_INT >= 30) {
            return node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
        }
        return false
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

    private fun registerJarvisReceiver() {
        if (jarvisReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(ACTION_EXECUTE_NOW)
            addAction(ACTION_GLOBAL_COMMAND)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(jarvisReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else {
            @Suppress("DEPRECATION")
            registerReceiver(jarvisReceiver, filter)
        }
        jarvisReceiverRegistered = true
    }

    override fun onInterrupt() {
        handler.removeCallbacksAndMessages(null)
        activeTargetPackage = ""
    }

    override fun onDestroy() {
        if (jarvisReceiverRegistered) {
            try { unregisterReceiver(jarvisReceiver) } catch (_: Exception) {}
            jarvisReceiverRegistered = false
        }
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        const val ACTION_EXECUTE_NOW = "com.jarvis.assistant.JARVIS_EXECUTE_NOW"
        const val ACTION_GLOBAL_COMMAND = "com.jarvis.assistant.JARVIS_GLOBAL_COMMAND"
        const val EXTRA_INSTRUCTION = "instruction"
        const val EXTRA_GLOBAL_ACTION = "global_action"
    }
}
