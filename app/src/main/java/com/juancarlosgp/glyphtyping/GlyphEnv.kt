package com.juancarlosgp.glyphtyping

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageInstaller
import android.os.Build
import android.provider.Settings as SysSettings

/** Reglas del SDK Glyph según versión de Android y clave API. */
object GlyphEnv {
    const val DEBUG_SETTING = "nt_glyph_interface_debug_enable"
    const val ADB_COMMAND = "adb shell settings put global $DEBUG_SETTING 1"
    const val SHELL_COMMAND = "settings put global $DEBUG_SETTING 1"

    /** Android 16 (API 36) o superior: Nothing quitó la restricción de clave API. */
    val isAndroid16Plus: Boolean get() = Build.VERSION.SDK_INT >= 36

    val hasRealKey: Boolean get() = BuildConfig.HAS_REAL_NOTHING_KEY

    /** En Android 14/15 con la clave "test" el SDK solo funciona con el modo debug activo. */
    val debugModeRequired: Boolean get() = !isAndroid16Plus && !hasRealKey

    fun debugModeEnabled(context: Context): Boolean = try {
        SysSettings.Global.getInt(context.contentResolver, DEBUG_SETTING, 0) == 1
    } catch (e: Exception) { false }

    fun accessibilityEnabled(context: Context): Boolean {
        val me = ComponentName(context, TypingAccessibilityService::class.java).flattenToString()
        val list = SysSettings.Secure.getString(context.contentResolver,
            SysSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return list.split(':').any { it.equals(me, ignoreCase = true) }
    }

    /** ¿Instalada desde un archivo APK? Entonces Android 13+ aplica "ajustes restringidos". */
    fun sideloaded(context: Context): Boolean = try {
        val src = context.packageManager.getInstallSourceInfo(context.packageName).packageSource
        src == PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE ||
            src == PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE
    } catch (e: Exception) { true }

    /** Texto de estado del modo debug para mostrar en pantalla. */
    fun debugStatusText(context: Context): String = when {
        isAndroid16Plus -> "no necesario (Android 16+)"
        hasRealKey -> "no necesario (clave oficial de Nothing)"
        debugModeEnabled(context) -> "activo (caduca a las 48 h)"
        else -> "NO ACTIVO ⚠ necesario en Android 14/15 sin clave oficial"
    }
}
