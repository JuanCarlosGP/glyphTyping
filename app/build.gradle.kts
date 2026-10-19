import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Glyph Developer Kit oficial de Nothing (com.nothing.ketchum), fijado a un commit concreto.
// Fuente: https://github.com/Nothing-Developer-Programme/Glyph-Developer-Kit
val glyphSdkCommit = "8ee807a9312a640b0d43051450924e3446bc1d78"
val glyphSdkSha256 = "329393019db5f0f987c6245855d13fa273d06756c68829ca0f6ae686ba336da1"
val glyphSdkUrl = "https://raw.githubusercontent.com/Nothing-Developer-Programme/Glyph-Developer-Kit/" +
    "$glyphSdkCommit/sdk/glyph-matrix-sdk-2.0.aar"
val glyphSdkFile = layout.projectDirectory.file("libs/glyph-sdk.aar").asFile

android {
    namespace = "com.juancarlosgp.glyphtyping"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.juancarlosgp.glyphtyping"
        minSdk = 34      // El SDK Glyph solo funciona en Nothing OS sobre Android 14+
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        manifestPlaceholders["nothingKey"] = (project.findProperty("nothingKey") as String?) ?: "test"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

val downloadGlyphSdk by tasks.registering {
    description = "Descarga el AAR oficial del Glyph SDK (no se incluye en el repo)."
    outputs.file(glyphSdkFile)
    doLast {
        fun sha(f: File) = MessageDigest.getInstance("SHA-256").digest(f.readBytes())
            .joinToString("") { "%02x".format(it) }
        if (!glyphSdkFile.exists() || sha(glyphSdkFile) != glyphSdkSha256) {
            glyphSdkFile.parentFile.mkdirs()
            println("Descargando Glyph SDK de Nothing: $glyphSdkUrl")
            uri(glyphSdkUrl).toURL().openStream().use { input ->
                glyphSdkFile.outputStream().use { input.copyTo(it) }
            }
            check(sha(glyphSdkFile) == glyphSdkSha256) { "SHA-256 del Glyph SDK no coincide" }
        }
    }
}
tasks.named("preBuild") { dependsOn(downloadGlyphSdk) }

dependencies {
    implementation(files(glyphSdkFile))
}
