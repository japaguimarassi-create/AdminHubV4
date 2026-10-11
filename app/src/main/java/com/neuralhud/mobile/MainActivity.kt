package com.neuralhud.mobile

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var status: TextView
    private var permissionScreenOpened = false
    private var hudLaunched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(16, 24, 32)
        window.navigationBarColor = Color.rgb(16, 24, 32)
        renderFallbackScreen()

        if (Settings.canDrawOverlays(this)) {
            launchHudAndClose()
        } else {
            requestOverlayPermission()
        }
    }

    private fun renderFallbackScreen() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(22), dp(28), dp(22), dp(28))
            setBackgroundColor(Color.rgb(16, 24, 32))
        }
        val title = TextView(this).apply {
            text = "NEURAL HUD"
            textSize = 28f
            setTextColor(Color.rgb(77, 208, 225))
        }
        page.addView(title, matchWrap())
        val description = TextView(this).apply {
            text = "O HUD flutuante precisa de permissão do Android. Depois de permitir, a esfera e o campo de comando serão abertos automaticamente."
            textSize = 15f
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(12), 0, dp(18))
        }
        page.addView(description, matchWrap())
        status = TextView(this).apply {
            text = "Solicitando permissão de sobreposição…"
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(Color.rgb(29, 43, 54))
        }
        page.addView(status, matchWrap())
        addButton(page, "Pedir permissão novamente") { requestOverlayPermission() }
        addButton(page, "Abrir configuração da IA (Acessibilidade)") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        addButton(page, "Iniciar HUD") {
            if (Settings.canDrawOverlays(this)) launchHudAndClose() else requestOverlayPermission()
        }
        setContentView(page)
    }

    private fun requestOverlayPermission() {
        if (Settings.canDrawOverlays(this)) {
            launchHudAndClose()
            return
        }
        permissionScreenOpened = true
        status.text = "Permita “Exibir sobre outros apps” para o Neural HUD. Ao voltar, a esfera será iniciada automaticamente."
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
        }
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized && Settings.canDrawOverlays(this) && !hudLaunched) {
            launchHudAndClose()
        } else if (::status.isInitialized && permissionScreenOpened && !Settings.canDrawOverlays(this)) {
            status.text = "A permissão ainda não foi concedida. Toque em “Pedir permissão novamente” para tentar outra vez."
        }
    }

    private fun launchHudAndClose() {
        if (hudLaunched || !Settings.canDrawOverlays(this)) return
        hudLaunched = true
        val intent = Intent(this, HudOverlayService::class.java).putExtra("open_panel", true)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
            finish()
        } catch (e: Exception) {
            hudLaunched = false
            if (::status.isInitialized) status.text = "Não foi possível iniciar o HUD: ${e.message ?: e.javaClass.simpleName}"
            Toast.makeText(this, "O Android impediu a inicialização do HUD.", Toast.LENGTH_LONG).show()
        }
    }

    private fun addButton(parent: LinearLayout, label: String, action: () -> Unit) {
        val button = Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(39, 69, 83))
            setOnClickListener { action() }
        }
        parent.addView(button, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(8)
        })
    }

    private fun matchWrap() = LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
