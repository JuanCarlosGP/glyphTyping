package com.juancarlosgp.glyphtyping

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.provider.Settings as SysSettings
import android.util.Log
import com.nothing.ketchum.Common
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphFrame
import com.nothing.ketchum.GlyphManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Envuelve el SDK Glyph (com.nothing.ketchum) para el Nothing Phone (1) = Glyph.DEVICE_20111.
 * Flujo: init() (bindService) -> onServiceConnected -> register(DEVICE_20111) -> openSession().
 * Todas las llamadas al SDK van en un hilo propio, con límite de frecuencia y cada paso se
 * registra en un log visible en la pantalla de la app.
 * No recibe ni guarda texto: solo la señal "ha habido una pulsación".
 */
class GlyphController private constructor(private val app: Context) {

    enum class State { DISCONNECTED, CONNECTING, READY, UNSUPPORTED, ERROR }

    @Volatile var state: State = State.DISCONNECTED
        private set
    @Volatile var lastError: String? = null
        private set

    private val settings = Settings(app)
    private val thread = HandlerThread("glyphTyping-glyph").apply { start() }
    private val handler = Handler(thread.looper)
    private val tracker = WpmTracker()
    private var manager: GlyphManager? = null
    private var refs = 0
    private var lastCommandAt = 0L
    private var zoneIndex = 0
    private var barPct = 0
    private var barTickScheduled = false

    // ---- Log visible en pantalla -------------------------------------------------------
    private val logLines = ArrayDeque<String>()
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun log(msg: String) {
        Log.d(TAG, msg)
        synchronized(logLines) {
            logLines.addLast("${timeFmt.format(Date())} $msg")
            while (logLines.size > 60) logLines.removeFirst()
        }
    }

    fun logText(): String = synchronized(logLines) { logLines.joinToString("\n") }

    /** Líneas que el propio SDK escribe con la etiqueta GlyphManager (logcat de nuestro proceso). */
    private fun sdkLogTail(): String = try {
        val p = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-t", "30", "GlyphManager:V", "*:S"))
        val t = p.inputStream.bufferedReader().readText().trim().lines()
            .filter { it.isNotBlank() && !it.startsWith("-----") }.takeLast(6)
        t.joinToString("\n") { it.substringAfter("GlyphManager").trim() }
    } catch (e: Exception) { "" }

    // ---- Diagnóstico -------------------------------------------------------------------
    /** true si Settings.Global nt_glyph_interface_debug_enable == 1 */
    fun debugModeEnabled(): Boolean = try {
        SysSettings.Global.getInt(app.contentResolver, "nt_glyph_interface_debug_enable", 0) == 1
    } catch (e: Exception) { false }

    private fun glyphServicePackageVisible(): Boolean = try {
        app.packageManager.getPackageInfo("com.nothing.thirdparty", 0); true
    } catch (e: PackageManager.NameNotFoundException) { false }

    private fun glyphServiceResolvable(): Boolean = try {
        val i = Intent("com.nothing.thirdparty.bind_glyphservice").setPackage("com.nothing.thirdparty")
        app.packageManager.queryIntentServices(i, 0).isNotEmpty()
    } catch (e: Exception) { false }

    private fun fail(s: State, msg: String) {
        state = s
        lastError = msg
        log("ERROR: $msg")
    }

    private val connectTimeout = Runnable {
        if (state == State.CONNECTING) {
            val sdk = sdkLogTail()
            fail(State.ERROR,
                "Sin onServiceConnected tras 4 s. Paquete com.nothing.thirdparty visible=" +
                    "${glyphServicePackageVisible()}, servicio resoluble=${glyphServiceResolvable()}." +
                    if (sdk.isNotEmpty()) "\nSDK: $sdk" else "")
        }
    }

    private val callback = object : GlyphManager.Callback {
        override fun onServiceConnected(name: ComponentName) {
            handler.post {
                handler.removeCallbacks(connectTimeout)
                log("servicio conectado (${name.flattenToShortString()})")
                try {
                    val gm = manager ?: return@post
                    if (!gm.register(Glyph.DEVICE_20111)) {
                        val dbg = if (debugModeEnabled()) "" else " El modo debug NO está activo."
                        val sdk = sdkLogTail()
                        fail(State.ERROR, "register(DEVICE_20111) devolvió false (clave API 'test' " +
                            "requiere modo debug activo; o clave inválida).$dbg" +
                            if (sdk.isNotEmpty()) "\nSDK: $sdk" else "")
                        return@post
                    }
                    log("registrado (DEVICE_20111)")
                    gm.openSession()
                    log("sesión abierta")
                    state = State.READY
                    lastError = null
                } catch (e: Throwable) {
                    fail(State.ERROR, "$e")
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            handler.post {
                log("servicio desconectado")
                try { manager?.closeSession() } catch (_: Throwable) {}
                if (state == State.READY) state = State.DISCONNECTED
            }
        }
    }

    /** Conecta con el servicio Glyph (contado por referencias: servicio + pantalla de ajustes). */
    fun acquire() {
        handler.post {
            refs++
            if (refs == 1) connect()
        }
    }

    fun release() {
        handler.post {
            if (refs > 0 && --refs == 0) disconnect()
        }
    }

    /** Reintenta la conexión desde cero (botón de la pantalla de ajustes). */
    fun reconnect() {
        handler.post {
            log("reconectando…")
            val n = refs
            if (n > 0) { disconnect(); refs = n; connect() }
        }
    }

    private fun connect() {
        log("modo debug (nt_glyph_interface_debug_enable)=${debugModeEnabled()}")
        if (!Common.is20111()) {
            fail(State.UNSUPPORTED, "Este móvil no es un Nothing Phone (1) (Common.is20111()=false)")
            return
        }
        if (!glyphServicePackageVisible()) {
            log("AVISO: com.nothing.thirdparty no es visible/instalado")
        }
        state = State.CONNECTING
        lastError = null
        try {
            val gm = GlyphManager.getInstance(app)
            manager = gm
            log("enlazando con com.nothing.thirdparty.GlyphService…")
            gm.init(callback)
            handler.postDelayed(connectTimeout, 4000)
        } catch (e: Throwable) {
            fail(State.ERROR, "$e")
        }
    }

    private fun disconnect() {
        handler.removeCallbacksAndMessages(null)
        try { manager?.turnOff() } catch (_: Throwable) {}
        try { manager?.closeSession() } catch (_: Throwable) {}
        try { manager?.unInit() } catch (_: Throwable) {}
        manager = null
        tracker.clear()
        barPct = 0
        barTickScheduled = false
        refs = 0
        state = State.DISCONNECTED
        log("desconectado")
    }

    /** Llamar por cada cambio de texto (sin contenido). */
    fun onTyping() {
        val now = SystemClock.elapsedRealtime()
        handler.post { handleTyping(now) }
    }

    /** Efecto de prueba desde la pantalla de ajustes (con destello largo, bien visible). */
    fun test() {
        handler.post {
            log("prueba: estado=$state modo=${settings.mode}")
            if (state != State.READY) {
                log("prueba cancelada: el Glyph no está listo${lastError?.let { " ($it)" } ?: ""}")
                return@post
            }
            lastCommandAt = 0
            when (settings.mode) {
                Mode.BAR -> {
                    val now = SystemClock.elapsedRealtime()
                    repeat(30) { tracker.record(now - it * 100L) }
                    updateBar()
                }
                else -> flash(SystemClock.elapsedRealtime(), settings.zones(), 700L)
            }
            log("prueba enviada")
        }
    }

    private fun handleTyping(now: Long) {
        if (!settings.enabled || state != State.READY) return
        when (settings.mode) {
            Mode.FLASH -> flash(now, settings.zones())
            Mode.ZONES -> {
                val zones = settings.zones()
                zoneIndex = (zoneIndex + 1) % zones.size
                flash(now, listOf(zones[zoneIndex]))
            }
            Mode.BAR -> {
                tracker.record(now)
                updateBar()
            }
        }
    }

    private fun frameFor(zones: List<Zone>): GlyphFrame {
        val b = GlyphFrame.Builder(Glyph.DEVICE_20111)
        zones.forEach {
            when (it) {
                Zone.A -> b.buildChannelA()
                Zone.B -> b.buildChannelB()
                Zone.C -> b.buildChannelC()
                Zone.D -> b.buildChannelD()
                Zone.E -> b.buildChannelE()
            }
        }
        return b.build()
    }

    private val turnOffRunnable = Runnable {
        try { manager?.turnOff() } catch (e: Throwable) { fail(State.ERROR, "turnOff: $e") }
    }

    private fun throttled(now: Long): Boolean {
        if (now - lastCommandAt < settings.minIntervalMs) return true
        lastCommandAt = now
        return false
    }

    private fun flash(now: Long, zones: List<Zone>, durationMs: Long = settings.flashMs.toLong()) {
        if (throttled(now)) return
        try {
            manager?.toggle(frameFor(zones))
            handler.removeCallbacks(turnOffRunnable)
            handler.postDelayed(turnOffRunnable, durationMs)
        } catch (e: Throwable) {
            fail(State.ERROR, "toggle: $e")
        }
    }

    private fun updateBar() {
        val now = SystemClock.elapsedRealtime()
        val pct = (tracker.wpm(now) / settings.maxWpm * 100).toInt().coerceIn(0, 100)
        if (pct != barPct && !throttled(now)) {
            barPct = pct
            try {
                if (pct == 0) manager?.turnOff()
                // En el Phone (1) displayProgress exige un frame con D1_1 (buildChannelD lo incluye)
                else manager?.displayProgress(frameFor(listOf(Zone.D)), pct)
            } catch (e: Throwable) {
                fail(State.ERROR, "displayProgress: $e")
            }
        }
        if ((barPct > 0 || pct > 0) && !barTickScheduled) {
            barTickScheduled = true
            handler.postDelayed({
                barTickScheduled = false
                if (settings.mode == Mode.BAR && state == State.READY) updateBar()
            }, maxOf(settings.minIntervalMs.toLong(), 150L))
        }
    }

    companion object {
        private const val TAG = "glyphTyping"
        @Volatile private var instance: GlyphController? = null
        fun get(context: Context): GlyphController =
            instance ?: synchronized(this) {
                instance ?: GlyphController(context.applicationContext).also { instance = it }
            }
    }
}
