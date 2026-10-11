package com.neuralhud.mobile

import android.app.Activity
import android.app.role.RoleManager
import android.Manifest
import android.content.pm.PackageManager
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
    private var configureAssistantScreen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(13, 22, 33)
        window.navigationBarColor = Color.rgb(13, 22, 33)
        JarvisLocalBrain.prewarm(this)
        configureAssistantScreen = intent.getBooleanExtra(EXTRA_CONFIGURE_ASSISTANT, false)
        renderScreen()

        if (configureAssistantScreen) {
            status.text = "Configure J.A.R.V.I.S. como assistente padrão do Android. O sistema pedirá sua confirmação."
        } else if (Settings.canDrawOverlays(this)) {
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
            text = "J.A.R.V.I.S."
            textSize = 27f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(91, 224, 238))
        }, matchWrap())
        page.addView(TextView(this).apply {
            text = "Assistente local, comandos globais e HUD flutuante."
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
        addButton(page, "Definir J.A.R.V.I.S. como assistente", true) {
            requestAssistantRole()
        }
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

        SystemBarInsets.apply(page)
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
        if (::status.isInitialized && !configureAssistantScreen && Settings.canDrawOverlays(this) && !hudLaunched) {
            launchHud()
        } else if (::status.isInitialized && permissionScreenOpened && !Settings.canDrawOverlays(this)) {
            status.text = "A permissão continua desligada. Toque em “Permitir e continuar” para tentar novamente."
        }
    }

    private fun requestAssistantRole() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            status.text = "Este Android não oferece a seleção de assistente via RoleManager."
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            @Suppress("DEPRECATION")
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC_FOR_ASSISTANT)
            return
        }
        launchAssistantRolePicker()
    }

    private fun launchAssistantRolePicker() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val manager = getSystemService(RoleManager::class.java)
        if (manager == null || !manager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
            status.text = "Este Android não disponibilizou a função de assistente padrão. O HUD e os comandos locais continuam disponíveis."
            return
        }
        if (manager.isRoleHeld(RoleManager.ROLE_ASSISTANT)) {
            status.text = "J.A.R.V.I.S. já é o assistente padrão. Use o gesto de assistente definido nas configurações do telefone."
            return
        }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(manager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT), REQUEST_ASSISTANT_ROLE)
            status.text = "Confirme na janela do Android para escolher J.A.R.V.I.S. como assistente."
        } catch (e: Exception) {
            status.text = "Não consegui abrir a seleção de assistente: " + (e.message ?: e.javaClass.simpleName)
        }
    }

    @Deprecated("Uses Android role request result")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_MIC_FOR_ASSISTANT) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                status.text = "Microfone permitido. Agora confirme o assistente padrão."
            } else {
                status.text = "Microfone negado; o assistente continuará aceitando comandos digitados."
            }
            launchAssistantRolePicker()
        }
    }

    @Deprecated("Uses Android role request result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ASSISTANT_ROLE) {
            status.text = if (resultCode == RESULT_OK) {
                "J.A.R.V.I.S. foi escolhido como assistente. Configure o gesto de invocação nas opções do Android."
            } else {
                "A escolha do assistente foi cancelada. Você pode tentar novamente."
            }
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

    companion object {
        const val EXTRA_CONFIGURE_ASSISTANT = "configure_jarvis_assistant"
        private const val REQUEST_ASSISTANT_ROLE = 6801
        private const val REQUEST_MIC_FOR_ASSISTANT = 6802
    }
}
