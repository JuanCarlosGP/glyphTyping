package com.juancarlosgp.glyphtyping

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import com.nothing.ketchum.Common
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphFrame
import com.nothing.ketchum.GlyphManager

/**
 * Envuelve el SDK Glyph (com.nothing.ketchum) para el Nothing Phone (1) = Glyph.DEVICE_20111.
 * Todas las llamadas al SDK se hacen en un hilo propio y con límite de frecuencia.
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

    private val callback = object : GlyphManager.Callback {
        override fun onServiceConnected(name: ComponentName) {
            handler.post {
                try {
                    val gm = manager ?: return@post
                    if (!gm.register(Glyph.DEVICE_20111)) {
                        fail(State.ERROR, "register() devolvió false (¿modo debug / clave API?)")
                        return@post
                    }
                    gm.openSession()
                    state = State.READY
                    lastError = null
                } catch (e: Exception) {
                    fail(State.ERROR, e.message ?: e.javaClass.simpleName)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            handler.post {
                try { manager?.closeSession() } catch (_: Exception) {}
                if (state == State.READY) state = State.DISCONNECTED
            }
        }
    }

    private fun fail(s: State, msg: String) {
        state = s
        lastError = msg
        Log.w(TAG, msg)
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

    private fun connect() {
        if (!Common.is20111()) {
            fail(State.UNSUPPORTED, "Este móvil no es un Nothing Phone (1)")
            return
        }
        state = State.CONNECTING
        try {
            val gm = GlyphManager.getInstance(app)
            manager = gm
            gm.init(callback)
        } catch (e: Exception) {
            fail(State.ERROR, e.message ?: e.javaClass.simpleName)
        }
    }

    private fun disconnect() {
        handler.removeCallbacksAndMessages(null)
        try { manager?.turnOff() } catch (_: Exception) {}
        try { manager?.closeSession() } catch (_: Exception) {}
        try { manager?.unInit() } catch (_: Exception) {}
        manager = null
        tracker.clear()
        barPct = 0
        barTickScheduled = false
        state = State.DISCONNECTED
    }

    /** Llamar por cada cambio de texto (sin contenido). */
    fun onTyping() {
        val now = SystemClock.elapsedRealtime()
        handler.post { handleTyping(now) }
    }

    /** Efecto de prueba desde la pantalla de ajustes. */
    fun test() {
        handler.post {
            when (settings.mode) {
                Mode.BAR -> {
                    val now = SystemClock.elapsedRealtime()
                    repeat(30) { tracker.record(now - it * 100L) }
                    lastCommandAt = 0
                    updateBar()
                }
                else -> { lastCommandAt = 0; handleTyping(SystemClock.elapsedRealtime()) }
            }
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
        try { manager?.turnOff() } catch (e: Exception) { fail(State.ERROR, e.message ?: "turnOff") }
    }

    private fun throttled(now: Long): Boolean {
        if (now - lastCommandAt < settings.minIntervalMs) return true
        lastCommandAt = now
        return false
    }

    private fun flash(now: Long, zones: List<Zone>) {
        if (throttled(now)) return
        try {
            manager?.toggle(frameFor(zones))
            handler.removeCallbacks(turnOffRunnable)
            handler.postDelayed(turnOffRunnable, settings.flashMs.toLong())
        } catch (e: Exception) {
            fail(State.ERROR, e.message ?: "toggle")
        }
    }

    private fun updateBar() {
        val now = SystemClock.elapsedRealtime()
        val pct = (tracker.wpm(now) / settings.maxWpm * 100).toInt().coerceIn(0, 100)
        // Solo enviamos si cambia el valor y respetando el intervalo mínimo
        if (pct != barPct && !throttled(now)) {
            barPct = pct
            try {
                if (pct == 0) manager?.turnOff()
                else manager?.displayProgress(frameFor(listOf(Zone.D)), pct)
            } catch (e: Exception) {
                fail(State.ERROR, e.message ?: "displayProgress")
            }
        }
        // Mientras haya barra visible, seguimos refrescando para que "baje" al dejar de escribir
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
