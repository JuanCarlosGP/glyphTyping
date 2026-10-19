package com.juancarlosgp.glyphtyping

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings as SysSettings
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import rikka.shizuku.Shizuku

/**
 * Asistente de primer uso. Detecta el estado cada segundo y avanza solo:
 *  1) servicio de accesibilidad activado
 *  2) (solo Android 14/15 sin clave oficial) modo debug del Glyph
 *  3) prueba del Glyph
 */
class OnboardingActivity : Activity() {
    companion object { const val EXTRA_FORCE = "force" }

    private enum class Step { ACCESSIBILITY, DEBUG, TEST }

    private lateinit var settings: Settings
    private lateinit var glyph: GlyphController
    private val ui = Handler(Looper.getMainLooper())
    private var current: Step? = null
    private var autoTested = false
    private var shizukuBusy = false

    private lateinit var progress: TextView
    private lateinit var title: TextView
    private lateinit var body: TextView
    private lateinit var body2: TextView
    private lateinit var code: TextView
    private lateinit var code2: TextView
    private lateinit var note: TextView
    private lateinit var btn1: Button
    private lateinit var btn2: Button
    private lateinit var btn3: Button

    private val tick = object : Runnable {
        override fun run() { render(); ui.postDelayed(this, 1000) }
    }

    private val permListener = Shizuku.OnRequestPermissionResultListener { _, result ->
        ui.post {
            if (result == PackageManager.PERMISSION_GRANTED) runShizuku()
            else toast("Permiso de Shizuku denegado")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings(this)
        glyph = GlyphController.get(this)

        if (!intent.getBooleanExtra(EXTRA_FORCE, false) && settings.onboardingDone &&
            GlyphEnv.accessibilityEnabled(this) &&
            (!GlyphEnv.debugModeRequired || GlyphEnv.debugModeEnabled(this))
        ) {
            startActivity(Intent(this, SettingsActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_onboarding)
        findViewById<View>(R.id.root).applySystemBarPadding()
        progress = findViewById(R.id.progress)
        title = findViewById(R.id.step_title)
        body = findViewById(R.id.step_body)
        body2 = findViewById(R.id.step_body2)
        code = findViewById(R.id.code)
        code2 = findViewById(R.id.code2)
        note = findViewById(R.id.note)
        btn1 = findViewById(R.id.btn1)
        btn2 = findViewById(R.id.btn2)
        btn3 = findViewById(R.id.btn3)
        findViewById<Button>(R.id.btn_advanced).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        try { Shizuku.addRequestPermissionResultListener(permListener) } catch (_: Throwable) {}
    }

    override fun onDestroy() {
        try { Shizuku.removeRequestPermissionResultListener(permListener) } catch (_: Throwable) {}
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        if (!::title.isInitialized) return
        glyph.acquire()
        ui.post(tick)
    }

    override fun onStop() {
        if (::title.isInitialized) {
            ui.removeCallbacks(tick)
            glyph.release()
        }
        super.onStop()
    }

    private fun currentStep(): Step = when {
        !GlyphEnv.accessibilityEnabled(this) -> Step.ACCESSIBILITY
        GlyphEnv.debugModeRequired && !GlyphEnv.debugModeEnabled(this) -> Step.DEBUG
        else -> Step.TEST
    }

    private val steps: List<Step>
        get() = if (GlyphEnv.debugModeRequired) Step.values().toList()
        else listOf(Step.ACCESSIBILITY, Step.TEST)

    private fun render() {
        val step = currentStep()
        val idx = steps.indexOf(step)
        progress.text = steps.indices.joinToString("  ") { if (it <= idx) "●" else "○" } +
            "   PASO ${idx + 1}/${steps.size}"
        if (step != current) {
            current = step
            autoTested = false
        }
        listOf(code, code2, body2, btn2, btn3).forEach { it.visibility = View.GONE }
        btn1.isEnabled = true
        when (step) {
            Step.ACCESSIBILITY -> renderAccessibility()
            Step.DEBUG -> renderDebug()
            Step.TEST -> renderTest()
        }
    }

    private fun renderAccessibility() {
        title.text = "Activa el servicio"
        body.text = "glyphTyping necesita un servicio de accesibilidad para saber CUÁNDO escribes " +
            "(no QUÉ escribes). Pulsa el botón, entra en «glyphTyping» y actívalo."
        btn1.text = "Abrir ajuste del servicio"
        btn1.setOnClickListener { openAccessibilityDetails() }
        if (GlyphEnv.sideloaded(this)) {
            body2.visibility = View.VISIBLE
            body2.text = "¿El interruptor sale gris o dice «Ajuste restringido»? Android lo bloquea " +
                "en apps instaladas desde un APK. Abre la ficha de la app → menú ⋮ (arriba a la " +
                "derecha) → «Permitir ajustes restringidos», y vuelve a intentarlo."
            btn2.visibility = View.VISIBLE
            btn2.text = "Abrir ficha de la app"
            btn2.setOnClickListener {
                startActivity(Intent(SysSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")))
            }
        }
        note.text = "Esta pantalla avanzará sola cuando el servicio esté activo."
    }

    private fun renderDebug() {
        title.text = "Activa el modo debug"
        body.text = "En Android 14/15 Nothing solo deja usar el Glyph a apps de terceros con clave " +
            "oficial o con el «modo debug» activado. Se desactiva solo a las 48 h y habrá que " +
            "repetirlo. En Android 16 (Nothing OS 4) ya no hace falta.\n\nOpción A — con un PC (adb):"
        code.visibility = View.VISIBLE
        code.text = GlyphEnv.ADB_COMMAND
        body2.visibility = View.VISIBLE
        body2.text = "Opción B — sin PC: instala Shizuku (actívalo con «Depuración inalámbrica») " +
            "y aShell, y en aShell ejecuta:"
        code2.visibility = View.VISIBLE
        code2.text = GlyphEnv.SHELL_COMMAND

        if (ShizukuHelper.isRunning()) {
            btn1.text = if (shizukuBusy) "Activando…" else "Activar modo debug con Shizuku"
            btn1.isEnabled = !shizukuBusy
            btn1.setOnClickListener { activateWithShizuku() }
            btn2.visibility = View.VISIBLE
            btn2.text = "Copiar orden de adb"
            btn2.setOnClickListener { copyToClipboard("adb", GlyphEnv.ADB_COMMAND) }
            btn3.visibility = View.VISIBLE
            btn3.text = "Copiar orden para aShell"
            btn3.setOnClickListener { copyToClipboard("shell", GlyphEnv.SHELL_COMMAND) }
            note.text = "Shizuku está en marcha: el botón ejecuta solo esa orden, tras pedirte permiso."
        } else {
            btn1.text = "Copiar orden de adb"
            btn1.setOnClickListener { copyToClipboard("adb", GlyphEnv.ADB_COMMAND) }
            btn2.visibility = View.VISIBLE
            btn2.text = "Copiar orden para aShell"
            btn2.setOnClickListener { copyToClipboard("shell", GlyphEnv.SHELL_COMMAND) }
            note.text = (if (ShizukuHelper.isInstalled(this))
                "Shizuku está instalado pero no en marcha: ábrelo e inícialo y aparecerá un botón para hacerlo automáticamente.\n\n"
            else "") + "Esta pantalla avanzará sola cuando el modo debug esté activo."
        }
    }

    private fun renderTest() {
        title.text = "Prueba el Glyph"
        when (glyph.state) {
            GlyphController.State.READY -> {
                if (!autoTested) { autoTested = true; glyph.test() }
                body.text = "Debería haberse encendido el Glyph trasero. ¿Lo has visto?\n\n" +
                    "A partir de ahora se iluminará al escribir con Gboard (o cualquier teclado)."
                btn1.text = "Sí, terminar"
                btn1.setOnClickListener {
                    settings.onboardingDone = true
                    startActivity(Intent(this, SettingsActivity::class.java))
                    finish()
                }
                btn2.visibility = View.VISIBLE
                btn2.text = "Repetir prueba"
                btn2.setOnClickListener { glyph.test() }
                note.text = "Cambia el efecto (destello, barra de velocidad, zonas) en Ajustes avanzados."
            }
            GlyphController.State.CONNECTING, GlyphController.State.DISCONNECTED -> {
                body.text = "Conectando con el Glyph…"
                btn1.text = "Reconectar"
                btn1.setOnClickListener { glyph.reconnect() }
                note.text = ""
            }
            GlyphController.State.UNSUPPORTED -> {
                body.text = "Este móvil no es un Nothing Phone (1). Por ahora la app solo es compatible con él."
                btn1.text = "Ajustes avanzados"
                btn1.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
                note.text = ""
            }
            GlyphController.State.ERROR -> {
                body.text = "No se pudo usar el Glyph:\n${glyph.lastError}"
                btn1.text = "Reconectar"
                btn1.setOnClickListener { glyph.reconnect() }
                btn2.visibility = View.VISIBLE
                btn2.text = "Copiar registro"
                btn2.setOnClickListener { copyToClipboard("glyphTyping log", glyph.logText()) }
                note.text = "Si el problema sigue, abre una incidencia en GitHub pegando el registro."
            }
        }
    }

    private fun openAccessibilityDetails() {
        val cn = ComponentName(this, TypingAccessibilityService::class.java).flattenToString()
        // Ajuste concreto del servicio (Android 12+). Si el sistema no lo permite, lista general
        // de accesibilidad resaltando nuestro servicio.
        try {
            startActivity(Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
                .putExtra(Intent.EXTRA_COMPONENT_NAME, cn))
        } catch (e: RuntimeException) {
            val args = Bundle().apply { putString(":settings:fragment_args_key", cn) }
            startActivity(Intent(SysSettings.ACTION_ACCESSIBILITY_SETTINGS)
                .putExtra(":settings:fragment_args_key", cn)
                .putExtra(":settings:show_fragment_args", args))
        }
    }

    private fun activateWithShizuku() {
        if (ShizukuHelper.hasPermission()) runShizuku() else ShizukuHelper.requestPermission()
    }

    private fun runShizuku() {
        shizukuBusy = true
        render()
        ShizukuHelper.enableGlyphDebug(this) { _, msg ->
            shizukuBusy = false
            toast(msg)
            render()
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
