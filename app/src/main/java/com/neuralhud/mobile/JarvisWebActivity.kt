package com.jarvis.assistant

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class JarvisWebActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var address: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(7, 12, 22)
        window.navigationBarColor = Color.rgb(7, 12, 22)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(Color.rgb(7, 12, 22))
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(TextView(this).apply {
            text = "J.A.R.V.I.S.  /  WEB"
            textSize = 17f
            setTextColor(Color.rgb(78, 217, 255))
        }, LinearLayout.LayoutParams(0, dp(44), 1f))
        top.addView(TextView(this).apply {
            text = "FECHAR"
            textSize = 10f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dp(12), dp(9), dp(12), dp(9))
            background = rounded(Color.rgb(17, 35, 54), Color.rgb(36, 90, 124))
            setOnClickListener { finish() }
        })
        root.addView(top)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(Color.rgb(13, 25, 41), Color.rgb(34, 88, 122))
            setPadding(dp(5), dp(4), dp(5), dp(4))
        }
        address = EditText(this).apply {
            hint = "Pesquisar ou inserir HTTPS"
            setHintTextColor(Color.rgb(105, 134, 156))
            setTextColor(Color.WHITE)
            textSize = 14f
            singleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setPadding(dp(10), dp(8), dp(6), dp(8))
            background = GradientDrawable().apply { setColor(Color.TRANSPARENT) }
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_SEARCH) { navigate(); true } else false
            }
        }
        row.addView(address, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(TextView(this).apply {
            text = "IR"
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(5, 18, 32))
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(Color.rgb(60, 208, 255), Color.rgb(60, 208, 255))
            setOnClickListener { navigate() }
        })
        root.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7); bottomMargin = dp(8) })
        web = WebView(this)
        web.settings.apply {
            javaScriptEnabled = false
            domStorageEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            builtInZoomControls = true
            displayZoomControls = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) safeBrowsingEnabled = true
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                return request.url.scheme?.lowercase() != "https"
            }
            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = !url.startsWith("https://", true)
        }
        root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        val url = intent.getStringExtra(EXTRA_URL)
        val query = intent.getStringExtra(EXTRA_QUERY)
        if (!url.isNullOrBlank()) loadUrl(url) else search(query ?: "assistente JARVIS Android")
    }

    private fun navigate() {
        val value = address.text?.toString()?.trim().orEmpty()
        if (value.isBlank()) return
        if (value.startsWith("https://", true)) loadUrl(value)
        else if (value.startsWith("http://", true)) address.error = "Por segurança, use HTTPS."
        else if (value.matches(Regex("^[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(/.*)?$"))) loadUrl("https://" + value)
        else search(value)
    }

    private fun search(query: String) {
        address.setText(query)
        web.loadUrl("https://html.duckduckgo.com/html/?q=" + Uri.encode(query))
    }

    private fun loadUrl(value: String) {
        val uri = Uri.parse(value)
        if (uri.scheme != "https" || uri.host.isNullOrBlank()) {
            address.error = "Somente endereços HTTPS válidos são permitidos."
            return
        }
        address.setText(value)
        web.loadUrl(uri.toString())
    }

    private fun rounded(fill: Int, stroke: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        cornerRadius = dp(15).toFloat()
        setStroke(dp(1), stroke)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onBackPressed() {
        if (::web.isInitialized && web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        if (::web.isInitialized) {
            web.stopLoading()
            web.loadUrl("about:blank")
            web.clearHistory()
            web.removeAllViews()
            web.destroy()
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_QUERY = "jarvis_web_query"
        const val EXTRA_URL = "jarvis_web_url"
    }
}
