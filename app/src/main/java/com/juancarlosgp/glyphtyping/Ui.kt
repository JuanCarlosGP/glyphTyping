package com.juancarlosgp.glyphtyping

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.View
import android.view.WindowInsets
import android.widget.Toast

/** Android 15+/16 obliga a dibujar bajo las barras del sistema: añadimos el padding a mano. */
fun View.applySystemBarPadding() {
    val l = paddingLeft; val t = paddingTop; val r = paddingRight; val b = paddingBottom
    setOnApplyWindowInsetsListener { v, insets ->
        val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        v.setPadding(l + bars.left, t + bars.top, r + bars.right, b + bars.bottom)
        insets
    }
    requestApplyInsets()
}

fun Activity.copyToClipboard(label: String, text: String) {
    (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
        .setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(this, "Copiado", Toast.LENGTH_SHORT).show()
}
