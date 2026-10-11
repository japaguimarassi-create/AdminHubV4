package com.neuralhud.mobile

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var status: TextView
    private var permissionScreenOpened = false
    private var hudLaunched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(13, 22, 33)
        window.navigationBarColor = Color.rgb(13, 22, 33)
        renderScreen()

        if (Settings.canDrawOverlays(this)) {
            launchHud()
        } else {
            requestOverlayPermission()
        }
    }

    private fun renderScreen() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(28), dp(24), dp(24))
            setBackgroundColor(Color.rgb(13, 22, 33))
        }
        val icon = ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
            contentDescription = "Símbolo Neural HUD"
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        page.addView(icon, LinearLayout.LayoutParams(dp(82), dp(82)).apply { bottomMargin = dp(16) })

        page.addView(TextView(this).apply {
            text = "NEURAL HUD"
            textSize = 27f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(91, 224, 238))
        }, matchWrap())
        page.addView(TextView(this).apply {
            text = "Um painel flutuante. Um campo de comando. Ferramentas separadas."
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(174, 194, 207))
            setPadding(0, dp(8), 0, dp(24))
        }, matchWrap())

        status = TextView(this).apply {
            text = "O Android precisa autorizar a esfera flutuante."
            textSize = 14f
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(Color.WHITE)
            setPadding(dp(14), dp(13), dp(14), dp(13))
            background = rounded(Color.rgb(20, 32, 43))
        }
        page.addView(status, matchWrap())
        addButton(page, "Permitir e continuar", true) { requestOverlayPermission() }
        addButton(page, "Configurar automação", false) {
            try {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (_: Exception) {
                Toast.makeText(this, "Abra Configurações > Acessibilidade.", Toast.LENGTH_LONG).show()
            }
        }
        page.addView(TextView(this).apply {
            text = "A automação entre aplicativos é opcional e só funciona após ativação manual em Acessibilidade. Os comandos são interpretados localmente."
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(145, 167, 181))
            setPadding(0, dp(20), 0, 0)
        }, matchWrap())

        setContentView(page)
    }

    private fun requestOverlayPermission() {
        if (Settings.canDrawOverlays(this)) {
            launchHud()
            return
        }
        permissionScreenOpened = true
        status.text = "Na tela do Android, habilite “Permitir sobreposição” para o Neural HUD. Depois, volte ao app."
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + packageName)))
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
            } catch (e: Exception) {
                status.text = "Não consegui abrir a configuração: " + (e.message ?: "erro do Android")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized && Settings.canDrawOverlays(this) && !hudLaunched) {
            launchHud()
        } else if (::status.isInitialized && permissionScreenOpened && !Settings.canDrawOverlays(this)) {
            status.text = "A permissão continua desligada. Toque em “Permitir e continuar” para tentar novamente."
        }
    }

    private fun launchHud() {
        if (hudLaunched || !Settings.canDrawOverlays(this)) return
        hudLaunched = true
        val intent = Intent(this, HudOverlayService::class.java).putExtra("open_panel", true)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
            finish()
        } catch (e: Exception) {
            hudLaunched = false
            if (::status.isInitialized) status.text = "Não foi possível iniciar o HUD: " + (e.message ?: e.javaClass.simpleName)
            Toast.makeText(this, "O Android impediu a inicialização. Confira as permissões.", Toast.LENGTH_LONG).show()
        }
    }

    private fun addButton(parent: LinearLayout, title: String, primary: Boolean, action: () -> Unit) {
        val button = Button(this).apply {
            text = title
            isAllCaps = false
            textSize = 14f
            minHeight = dp(48)
            setTextColor(if (primary) Color.rgb(8, 23, 32) else Color.WHITE)
            background = rounded(if (primary) Color.rgb(91, 224, 238) else Color.rgb(34, 51, 65))
            setOnClickListener { action() }
        }
        parent.addView(button, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(8) })
    }

    private fun rounded(fill: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        cornerRadius = dp(14).toFloat()
        if (fill != Color.rgb(91, 224, 238)) setStroke(dp(1), Color.rgb(56, 86, 104))
    }

    private fun matchWrap() = LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
