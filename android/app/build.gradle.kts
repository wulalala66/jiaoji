plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val appVerCode = (project.findProperty("verCode") as String?)?.toIntOrNull() ?: 1
val appVerName = (project.findProperty("verName") as String?) ?: "2.0.0"

val ksFile = System.getenv("KEYSTORE_PATH")
val ksPass = System.getenv("KS_PASS") ?: ""
val ksAlias = System.getenv("KEY_ALIAS") ?: "jiaoji"

android {
    namespace = "com.wulalala66.jiaoji"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.wulalala66.jiaoji"
        minSdk = 24
        targetSdk = 34
        versionCode = appVerCode
        versionName = appVerName
    }

    signingConfigs {
        create("release") {
            if (!ksFile.isNullOrBlank() && File(ksFile).exists()) {
                storeFile = File(ksFile)
                setStorePassword(ksPass)
                keyAlias = ksAlias
                setKeyPassword(ksPass)
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val cfg = signingConfigs.getByName("release")
            if (cfg.storeFile != null) signingConfig = cfg
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json"
            )
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
