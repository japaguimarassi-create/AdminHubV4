package com.neuralhud.mobile

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AppObserverService : AccessibilityService() {
    private lateinit var model: LocalNeuralModel

    override fun onServiceConnected() {
        super.onServiceConnected()
        model = LocalNeuralModel(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return

        val packageName = event.packageName?.toString().orEmpty()
        if (packageName.isBlank() || packageName == this.packageName) return

        val root = rootInActiveWindow
        val sample = StringBuilder()
        val counter = intArrayOf(0)
        collectVisibleText(root, sample, counter)
        val signal = if (sample.isEmpty()) packageName else "$packageName ${sample}"
        val category = if (::model.isInitialized) model.classify(signal) else "Observação local"

        getSharedPreferences("neural_hud_observer", MODE_PRIVATE).edit()
            .putString("last_package", packageName)
            .putString("last_category", category)
            .putLong("last_observed_at", System.currentTimeMillis())
            .apply()
    }

    private fun collectVisibleText(node: AccessibilityNodeInfo?, output: StringBuilder, count: IntArray) {
        if (node == null || count[0] >= 160 || output.length >= 1800) return
        count[0]++
        if (!node.isPassword) {
            val text = node.text?.toString().orEmpty()
            val description = node.contentDescription?.toString().orEmpty()
            if (text.isNotBlank()) output.append(text).append(' ')
            if (description.isNotBlank()) output.append(description).append(' ')
        }
        for (index in 0 until node.childCount) {
            if (count[0] >= 160 || output.length >= 1800) break
            collectVisibleText(node.getChild(index), output, count)
        }
    }

    override fun onInterrupt() {
    }
}
