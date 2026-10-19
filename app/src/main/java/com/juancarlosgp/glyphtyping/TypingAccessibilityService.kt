package com.juancarlosgp.glyphtyping

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Escucha SOLO TYPE_VIEW_TEXT_CHANGED. NO lee event.text ni ningún contenido de la pantalla
 * (el servicio ni siquiera tiene canRetrieveWindowContent). Solo avisa de "ha habido un cambio".
 */
class TypingAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        GlyphController.get(this).acquire()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) return
        // Campos de contraseña: no reaccionamos (solo se consulta una marca booleana, no el texto)
        if (event.isPassword) return
        GlyphController.get(this).onTyping()
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        GlyphController.get(this).release()
        super.onDestroy()
    }
}
