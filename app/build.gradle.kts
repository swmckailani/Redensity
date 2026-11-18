plugins {
    id("com.android.application")
    kotlin("android")
}

import java.util.Properties

android {
    namespace = "com.redensity.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.redensity.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "1.5"
    }

    signingConfigs {
        create("release") {
            val propsFile = rootProject.file("local.properties")
            val props = Properties()
            if (propsFile.exists()) propsFile.inputStream().use { props.load(it) }
            val ks = props.getProperty("keystore.file")
                ?: System.getenv("REDENSITY_KEYSTORE")
                ?: rootProject.file("release.keystore").absolutePath
            if (file(ks).exists()) {
                storeFile = file(ks)
                storePassword = props.getProperty("keystore.password")
                    ?: System.getenv("REDENSITY_KEYSTORE_PASSWORD")
                keyAlias = props.getProperty("keystore.alias")
                    ?: System.getenv("REDENSITY_KEY_ALIAS")
                    ?: "redensity"
                keyPassword = props.getProperty("keystore.keyPassword")
                    ?: System.getenv("REDENSITY_KEY_PASSWORD")
            } else {
                println("Redensity: keystore $ks tidak ditemukan — release build tidak bisa di-sign.")
            }
        }
    }

    buildFeatures {
        aidl = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

dependencies {
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
