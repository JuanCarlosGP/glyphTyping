package com.juancarlosgp.glyphtyping

/**
 * Estima palabras por minuto SOLO a partir de cuántos eventos "texto cambiado" ocurren
 * por segundo (se guardan únicamente marcas de tiempo, nunca texto).
 * Convención: 1 palabra = 5 pulsaciones. Borrar también cuenta como pulsación.
 */
class WpmTracker(private val windowMs: Long = 5000) {
    private val times = ArrayDeque<Long>()

    fun record(nowMs: Long) {
        times.addLast(nowMs)
        trim(nowMs)
    }

    fun wpm(nowMs: Long): Double {
        trim(nowMs)
        val words = times.size / 5.0
        return words / (windowMs / 60000.0)
    }

    fun clear() = times.clear()

    private fun trim(nowMs: Long) {
        while (times.isNotEmpty() && nowMs - times.first() > windowMs) times.removeFirst()
    }
}
