package com.juancarlosgp.glyphtyping

import android.content.Context
import android.content.SharedPreferences

enum class Mode { FLASH, BAR, ZONES }

/** Zonas del Glyph del Phone (1). Máscara de bits para guardarlas. */
enum class Zone(val bit: Int, val label: String) {
    A(1, "A (anillo del cargador, arriba izq.)"),
    B(2, "B (arriba derecha)"),
    C(4, "C (anillo grande)"),
    D(8, "D (tira vertical)"),
    E(16, "E (punto inferior)")
}

/** Ajustes persistentes (SharedPreferences privadas; nunca contienen texto del usuario). */
class Settings(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("glyphtyping", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", true)
        set(v) = prefs.edit().putBoolean("enabled", v).apply()

    var mode: Mode
        get() = runCatching { Mode.valueOf(prefs.getString("mode", Mode.FLASH.name)!!) }
            .getOrDefault(Mode.FLASH)
        set(v) = prefs.edit().putString("mode", v.name).apply()

    var zonesMask: Int
        get() = prefs.getInt("zones", Zone.C.bit)
        set(v) = prefs.edit().putInt("zones", v).apply()

    /** Duración del destello en ms. */
    var flashMs: Int
        get() = prefs.getInt("flashMs", 60).coerceIn(20, 400)
        set(v) = prefs.edit().putInt("flashMs", v.coerceIn(20, 400)).apply()

    /** Intervalo mínimo entre órdenes al Glyph (límite de refresco). */
    var minIntervalMs: Int
        get() = prefs.getInt("minIntervalMs", 60).coerceIn(MIN_INTERVAL_MS, 500)
        set(v) = prefs.edit().putInt("minIntervalMs", v.coerceIn(MIN_INTERVAL_MS, 500)).apply()

    /** PPM que llenan la barra por completo. */
    var maxWpm: Int
        get() = prefs.getInt("maxWpm", 80).coerceIn(20, 200)
        set(v) = prefs.edit().putInt("maxWpm", v.coerceIn(20, 200)).apply()

    var onboardingDone: Boolean
        get() = prefs.getBoolean("onboardingDone", false)
        set(v) = prefs.edit().putBoolean("onboardingDone", v).apply()

    fun zones(): List<Zone> = Zone.values().filter { zonesMask and it.bit != 0 }
        .ifEmpty { listOf(Zone.C) }

    companion object {
        const val MIN_INTERVAL_MS = 30
    }
}
