# glyphTyping

App Android para el **Nothing Phone (1)** que ilumina el **Glyph** cuando escribes con Gboard (o cualquier teclado).

- Efectos configurables: **destello por tecla**, **barra que sube con tu velocidad (PPM)** y **zonas distintas**.
- Kotlin + Gradle, sin dependencias externas salvo el SDK Glyph oficial de Nothing.
- **Privacidad total**: no se lee ni se guarda el texto, no hay red.

## Para usuarios: instalar en 3 pasos

> Solo funciona en el **Nothing Phone (1)** con Nothing OS sobre Android 14 o superior.

1. **Descarga e instala** el último `glyphTyping-…-debug.apk` desde [Releases](https://github.com/JuanCarlosGP/glyphTyping/releases) (permite «instalar apps desconocidas» si te lo pide).
2. **Abre la app y sigue el asistente**:
   - *Paso 1 — Accesibilidad*: el botón abre directamente el ajuste del servicio; actívalo. Si sale gris («Ajuste restringido»), el asistente te lleva a la ficha de la app → ⋮ → «Permitir ajustes restringidos».
   - *Paso 2 — Modo debug* (solo Android 14/15, p. ej. Nothing OS 3.x): con PC `adb shell settings put global nt_glyph_interface_debug_enable 1`, o sin PC con **Shizuku** (botón «Activar modo debug con Shizuku») o Shizuku + aShell. Caduca a las 48 h. En **Android 16 / Nothing OS 4 no hace falta** y el paso no aparece.
   - *Paso 3 — Prueba*: el Glyph se enciende; confirma y listo.
3. **Escribe** con Gboard (o cualquier teclado) y el Glyph se iluminará. Cambia el efecto en *Ajustes avanzados*.

### Sobre la clave oficial de Nothing

El SDK Glyph exige una clave (`NothingKey`) en Android 14/15. Esta app se publica con la clave de pruebas `test`, que solo funciona con el modo debug. Nothing [eliminó esa restricción para apps con target Android 16](https://github.com/Nothing-Developer-Programme/Glyph-Developer-Kit#setup-development-environment) (esta app ya apunta a API 36). Si consigues una clave oficial (se solicita al programa de desarrolladores de Nothing), compila con `./gradlew assembleDebug -PnothingKey=TU_CLAVE` y el paso del modo debug desaparece.

## Cómo funciona

Un `AccessibilityService` escucha **únicamente** el evento `TYPE_VIEW_TEXT_CHANGED`. Cada vez que cambia un campo de texto, avisa al Glyph (SDK `com.nothing.ketchum`, `Glyph.DEVICE_20111`). Nunca se accede a `event.text` ni al contenido de la pantalla.

## Requisitos

- Nothing Phone (1) con Nothing OS sobre **Android 14 o superior** (requisito del SDK Glyph; `minSdk = 34`, `targetSdk = compileSdk = 36`).
- Para compilar: JDK 17 y Android SDK (plataforma 35).

## Instalación

### Opción A: APK de GitHub Actions
1. Ve a la pestaña **Actions** del repo → último workflow "Build APK debug" → artifact `glyphTyping-debug-apk`.
2. Descomprime el `.zip` y copia `app-debug.apk` al móvil (o `adb install app-debug.apk`).
3. Permite "instalar apps de origen desconocido" si el móvil lo pide.

### Opción B: compilar tú mismo
```bash
git clone https://github.com/JuanCarlosGP/glyphTyping.git
cd glyphTyping
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
El AAR del SDK Glyph **no está en este repo**: Gradle lo descarga de
[Nothing-Developer-Programme/Glyph-Developer-Kit](https://github.com/Nothing-Developer-Programme/Glyph-Developer-Kit)
(commit fijado `8ee807a`, con verificación SHA-256) a `app/libs/`. Ese repositorio no declara licencia, así que no se redistribuye el binario aquí; su uso se rige por los términos de Nothing.

## Modo debug del Glyph / clave de API (importante)

El SDK de Nothing exige una clave API (`NothingKey` en el manifest). Para desarrollo no hace falta pedirla: la app usa la clave `test`, que **solo funciona con el modo debug activado**:

```bash
adb shell settings put global nt_glyph_interface_debug_enable 1
```

- Al activarlo aparece una notificación del sistema.
- **Se desactiva solo a las 48 horas**: vuelve a ejecutar el comando cuando el Glyph deje de responder.
- Con una clave real: `./gradlew assembleDebug -PnothingKey=TU_CLAVE`.
- Nothing indica que la restricción de clave se elimina en Android 16 (B) para apps con target 36; la app la mantiene igualmente en el manifest (lo recomienda Nothing). En Android 16+ la app no pide el modo debug.
- Si `register()` falla por falta de modo debug, la app **no reintenta en bucle**: observa `Settings.Global` y se reconecta sola en cuanto el ajuste cambia (más un reintento con backoff de 30 s a 15 min para otros fallos).

### Diagnóstico sin adb (v0.1.1)

La pantalla principal muestra el estado de accesibilidad, si el modo debug está activo (`Settings.Global nt_glyph_interface_debug_enable`) y un **registro de conexión** (enlazando → servicio conectado → registrado → sesión abierta, o el error exacto). El botón *Copiar registro* lo copia al portapapeles y *Reconectar con el Glyph* reintenta. El manifest declara `<queries>` para `com.nothing.thirdparty` (visibilidad de paquetes en Android 11+, necesaria para que el SDK pueda enlazar con el servicio Glyph).

## Cómo activar la accesibilidad

1. Abre **glyphTyping** y pulsa **Abrir ajustes de accesibilidad** (o ve a *Ajustes → Accesibilidad → Apps instaladas / Servicios descargados*).
2. Elige **glyphTyping** y actívalo.
3. Android mostrará un aviso genérico de "este servicio puede observar tus acciones". Es el texto estándar del sistema; ver sección de privacidad.
4. Si Android bloquea la opción por ser una app instalada fuera de Play Store: *Ajustes → Apps → glyphTyping → ⋮ → Permitir ajustes restringidos* y repite.

## Permisos explicados

| Permiso / capacidad | Para qué |
|---|---|
| `com.nothing.ketchum.permission.ENABLE` | Permite usar el servicio Glyph del sistema. |
| Servicio de accesibilidad (`BIND_ACCESSIBILITY_SERVICE`) | Solo para recibir "ha cambiado un texto". Configurado con `typeViewTextChanged` y **`canRetrieveWindowContent=false`**. |
| Shizuku (`moe.shizuku.manager.permission.API_V23` + `ShizukuProvider`) | **Opcional**. Si Shizuku está en marcha y lo autorizas, la app ejecuta con permisos de shell **una única orden fija**: `settings put global nt_glyph_interface_debug_enable 1` (ver `ShellUserService.kt`). Nada más. |
| **No** hay `INTERNET` | La app no puede enviar nada fuera del móvil. Puedes comprobarlo con `aapt2 dump permissions`. |

## Efectos y ajustes

| Efecto | Qué hace |
|---|---|
| Destello por tecla | Enciende las zonas elegidas un instante (duración configurable). |
| Barra (PPM) | La tira **D** se llena según tu velocidad (1 palabra = 5 pulsaciones, ventana de 5 s) y baja cuando paras. Configurable: PPM que llenan la barra. |
| Zonas distintas | Cada pulsación enciende la siguiente zona de las elegidas (A, B, C, D, E). |

**Límite de frecuencia**: se ignora cualquier pulsación que llegue antes del *intervalo mínimo* (por defecto 60 ms, mínimo 30 ms) para no saturar el servicio Glyph. El SDK no permite regular el brillo.

Limitación conocida: como no se lee el texto, no se distingue escribir de borrar; ambos cuentan como pulsación, y la escritura por deslizamiento (swipe) genera un solo evento por palabra.

## Privacidad

- No se lee, procesa, almacena ni transmite el texto escrito. El código nunca toca `event.text` ni `AccessibilityNodeInfo`.
- Del evento solo se usa su tipo y si el campo es de contraseña (para ignorarlo).
- Solo se guardan tus ajustes de efectos (`SharedPreferences` privadas). `allowBackup=false`.
- Para PPM solo se mantienen marcas de tiempo en memoria de los últimos 5 s.
- Sin permiso `INTERNET`, sin analíticas, sin dependencias de terceros.

## Limitaciones y riesgos conocidos

- La documentación del SDK dice que **solo las apps en primer plano** pueden usar el Glyph. Un servicio de accesibilidad no es una app en primer plano, así que **puede que el Glyph no responda mientras escribes en otra app**. Está por verificar en un dispositivo real; ver Issues.
- Android 14+ puede mostrar avisos extra para servicios de accesibilidad no destinados a discapacidad.
- El modo debug caduca a las 48 h.

## CI

`.github/workflows/build.yml` compila `./gradlew assembleDebug` en cada push/PR y sube el APK como artifact.

## Licencia

MIT (ver [LICENSE](LICENSE)). Componentes de terceros:
- SDK Glyph de Nothing: se descarga en el build, con sus propios términos.
- [Shizuku API](https://github.com/RikkaApps/Shizuku-API) (`dev.rikka.shizuku`), Apache-2.0.
- Fuente [Doto](https://github.com/oliverlalan/Doto) (tipografía de puntos), SIL Open Font License 1.1 (ver [FONT-LICENSE-Doto-OFL.txt](FONT-LICENSE-Doto-OFL.txt)).
