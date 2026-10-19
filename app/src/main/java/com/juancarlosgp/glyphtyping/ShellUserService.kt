package com.juancarlosgp.glyphtyping

import kotlin.system.exitProcess

/**
 * Se ejecuta en un proceso aparte lanzado por Shizuku con permisos de shell (uid 2000).
 * SOLO ejecuta una orden fija: `settings put global nt_glyph_interface_debug_enable 1`.
 * No acepta órdenes ni argumentos desde fuera.
 */
class ShellUserService : IShellService.Stub() {
    override fun destroy() {
        exitProcess(0)
    }

    override fun enableGlyphDebug(): String {
        val p = ProcessBuilder("settings", "put", "global", GlyphEnv.DEBUG_SETTING, "1")
            .redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText().trim()
        val code = p.waitFor()
        return "$code|$out"
    }
}
