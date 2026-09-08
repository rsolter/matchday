import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.light.sdk)
}

// Release signing: reads examples/soccer-football/keystore.properties, a gitignored file holding a
// private release key (see keystore.properties.example for the format + the README's "Building it"
// section for how to generate one with keytool). That file intentionally never exists in a fresh
// clone — including this sandbox — so `hasReleaseKeystore` is false here and `release` falls back to
// the shared lightsdkDev key below, same as before this change. Only on a machine where that
// properties file has been created does `assembleRelease` sign with the private key instead.
val releaseKeystoreProperties = Properties().apply {
    val propsFile = project.file("keystore.properties")
    if (propsFile.exists()) {
        propsFile.inputStream().use { load(it) }
    }
}
val hasReleaseKeystore = releaseKeystoreProperties.getProperty("storeFile") != null

android {
    compileSdk = rootProject.ext["compileSdk"] as Int

    signingConfigs {
        // Shared, publicly-committed dev key (password "android") — fine for local/emulator testing,
        // but anyone with the repo can resign an APK with this same key, so it's not appropriate for
        // a build meant to be handed out as an authentic release. Used for `debug` always, and as the
        // `release` fallback only when no private keystore.properties is present.
        create("lightsdkDev") {
            storeFile = file("../../sdk/keys/lightsdk-dev.jks")
            storePassword = "android"
            keyAlias = "lightsdk-dev"
            keyPassword = "android"
            enableV3Signing = true
            enableV4Signing = true
        }
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = project.file(releaseKeystoreProperties.getProperty("storeFile"))
                storePassword = releaseKeystoreProperties.getProperty("storePassword")
                keyAlias = releaseKeystoreProperties.getProperty("keyAlias")
                keyPassword = releaseKeystoreProperties.getProperty("keyPassword")
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    defaultConfig {
        minSdk = rootProject.ext["minSdk"] as Int
        targetSdk = rootProject.ext["targetSdk"] as Int

        manifestPlaceholders["sdkVersion"] = property("sdkVersion") as String
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("lightsdkDev")
        }
        release {
            signingConfig = signingConfigs.getByName(if (hasReleaseKeystore) "release" else "lightsdkDev")
        }
    }

    lint {
        warningsAsErrors = false
        error += "RestrictedApi"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(rootProject.ext["jvmTarget"] as String)
        targetCompatibility = JavaVersion.toVersion(rootProject.ext["jvmTarget"] as String)
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(rootProject.ext["jvmTarget"] as String))
    }
}

dependencies {
    implementation(project(":sdk:client"))
    implementation(libs.kotlinx.datetime)
    testImplementation(libs.kotlin.test)
}
