package com.juancarlosgp.glyphtyping

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings as SysSettings
import android.widget.Button
import android.widget.CheckBox
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

class SettingsActivity : Activity() {
    private lateinit var settings: Settings
    private lateinit var glyph: GlyphController
    private lateinit var status: TextView
    private lateinit var logView: TextView
    private val ui = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() { updateStatus(); ui.postDelayed(this, 1000) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        settings = Settings(this)
        glyph = GlyphController.get(this)
        status = findViewById(R.id.status)
        logView = findViewById(R.id.log)
        findViewById<Button>(R.id.btn_reconnect).setOnClickListener { glyph.reconnect() }
        findViewById<Button>(R.id.btn_copylog).setOnClickListener {
            (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("glyphTyping log", glyph.logText()))
        }

        findViewById<Button>(R.id.btn_accessibility).setOnClickListener {
            startActivity(Intent(SysSettings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.btn_test).setOnClickListener { glyph.test() }

        findViewById<Switch>(R.id.sw_enabled).apply {
            isChecked = settings.enabled
            setOnCheckedChangeListener { _, c -> settings.enabled = c }
        }

        val modeIds = mapOf(Mode.FLASH to R.id.rb_flash, Mode.BAR to R.id.rb_bar, Mode.ZONES to R.id.rb_zones)
        findViewById<RadioGroup>(R.id.rg_mode).apply {
            check(modeIds.getValue(settings.mode))
            setOnCheckedChangeListener { _, id ->
                settings.mode = modeIds.entries.first { it.value == id }.key
            }
        }

        val zoneIds = mapOf(
            Zone.A to R.id.cb_a, Zone.B to R.id.cb_b, Zone.C to R.id.cb_c,
            Zone.D to R.id.cb_d, Zone.E to R.id.cb_e
        )
        zoneIds.forEach { (zone, id) ->
            findViewById<CheckBox>(id).apply {
                text = zone.label
                isChecked = settings.zonesMask and zone.bit != 0
                setOnCheckedChangeListener { _, c ->
                    settings.zonesMask =
                        if (c) settings.zonesMask or zone.bit else settings.zonesMask and zone.bit.inv()
                }
            }
        }

        bindSeek(R.id.sb_flash, R.id.tv_flash, "Duración del destello: %d ms", 20, 400,
            { settings.flashMs }, { settings.flashMs = it })
        bindSeek(R.id.sb_interval, R.id.tv_interval,
            "Intervalo mínimo entre órdenes al Glyph: %d ms", Settings.MIN_INTERVAL_MS, 500,
            { settings.minIntervalMs }, { settings.minIntervalMs = it })
        bindSeek(R.id.sb_wpm, R.id.tv_wpm, "PPM que llenan la barra: %d", 20, 200,
            { settings.maxWpm }, { settings.maxWpm = it })
    }

    private fun bindSeek(
        seekId: Int, labelId: Int, fmt: String, min: Int, max: Int,
        get: () -> Int, set: (Int) -> Unit
    ) {
        val sb = findViewById<SeekBar>(seekId)
        val tv = findViewById<TextView>(labelId)
        sb.max = max - min
        sb.progress = get() - min
        tv.text = fmt.format(get())
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                if (fromUser) { set(p + min); tv.text = fmt.format(get()) }
            }
            override fun onStartTrackingTouch(s: SeekBar) = Unit
            override fun onStopTrackingTouch(s: SeekBar) = Unit
        })
    }

    private fun accessibilityEnabled(): Boolean {
        val me = ComponentName(this, TypingAccessibilityService::class.java).flattenToString()
        val list = SysSettings.Secure.getString(contentResolver,
            SysSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return list.split(':').any { it.equals(me, ignoreCase = true) }
    }

    private fun updateStatus() {
        val acc = if (accessibilityEnabled()) "activado" else "DESACTIVADO (pulsa el botón de abajo)"
        val g = when (glyph.state) {
            GlyphController.State.READY -> "sesión abierta (listo)"
            GlyphController.State.CONNECTING -> "enlazando…"
            GlyphController.State.DISCONNECTED -> "desconectado"
            GlyphController.State.UNSUPPORTED -> "no compatible (solo Phone (1))"
            GlyphController.State.ERROR -> "ERROR: ${glyph.lastError}"
        }
        val dbg = if (glyph.debugModeEnabled()) "activo"
        else "NO ACTIVO ⚠ ejecuta en el PC: adb shell settings put global nt_glyph_interface_debug_enable 1"
        status.text = "Accesibilidad: $acc\nModo debug Glyph: $dbg\nGlyph: $g"
        logView.text = glyph.logText()
    }

    override fun onStart() {
        super.onStart()
        glyph.acquire()   // la pantalla en primer plano puede probar el Glyph
        ui.post(refresh)
    }

    override fun onStop() {
        ui.removeCallbacks(refresh)
        glyph.release()
        super.onStop()
    }
}
