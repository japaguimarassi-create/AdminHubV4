package com.neuralhud.mobile

import android.os.Build
import android.view.View
import android.view.WindowInsets

object SystemBarInsets {
    fun apply(root: View) {
        if (Build.VERSION.SDK_INT < 35) return
        val left = root.paddingLeft
        val top = root.paddingTop
        val right = root.paddingRight
        val bottom = root.paddingBottom
        root.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            view.setPadding(left, top + bars.top, right, bottom + bars.bottom)
            insets
        }
    }
}
