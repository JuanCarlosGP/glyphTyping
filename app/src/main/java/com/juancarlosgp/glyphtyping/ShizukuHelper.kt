package com.juancarlosgp.glyphtyping

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import rikka.shizuku.Shizuku

/** Integración opcional con Shizuku: solo para activar el modo debug del Glyph. */
object ShizukuHelper {
    private const val SHIZUKU_PKG = "moe.shizuku.privileged.api"
    const val REQUEST_CODE = 4242
    private val main = Handler(Looper.getMainLooper())

    fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(SHIZUKU_PKG, 0); true
    } catch (e: PackageManager.NameNotFoundException) { false }

    fun isRunning(): Boolean = try { Shizuku.pingBinder() } catch (e: Throwable) { false }

    fun hasPermission(): Boolean = try {
        isRunning() && !Shizuku.isPreV11() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Throwable) { false }

    fun requestPermission() {
        try { Shizuku.requestPermission(REQUEST_CODE) } catch (_: Throwable) {}
    }

    /** Ejecuta la orden fija vía un UserService de Shizuku. onResult(ok, mensaje) en hilo principal. */
    fun enableGlyphDebug(context: Context, onResult: (Boolean, String) -> Unit) {
        if (!hasPermission()) { onResult(false, "Shizuku no está en marcha o sin permiso"); return }
        val args = Shizuku.UserServiceArgs(ComponentName(context.packageName, ShellUserService::class.java.name))
            .daemon(false)
            .processNameSuffix("shell")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val conn = this
                Thread {
                    val result = try {
                        val svc = IShellService.Stub.asInterface(binder)
                        val r = svc.enableGlyphDebug()
                        val code = r.substringBefore('|').toIntOrNull() ?: -1
                        (code == 0) to (if (code == 0) "Modo debug activado" else "Error ($r)")
                    } catch (e: Throwable) {
                        false to "Error: $e"
                    }
                    try { Shizuku.unbindUserService(args, conn, true) } catch (_: Throwable) {}
                    main.post { onResult(result.first, result.second) }
                }.start()
            }
            override fun onServiceDisconnected(name: ComponentName?) = Unit
        }
        try {
            Shizuku.bindUserService(args, conn)
        } catch (e: Throwable) {
            onResult(false, "Error: $e")
        }
    }
}
