plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val appVerCode = (project.findProperty("verCode") as String?)?.toIntOrNull() ?: 1
val appVerName = (project.findProperty("verName") as String?) ?: "2.0.0"

android {
    namespace = "com.gluelab.jiaoji"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.gluelab.jiaoji"
        minSdk = 24
        targetSdk = 34
        versionCode = appVerCode
        versionName = appVerName
    }

    // 正式包由 CI 用 apksigner 签名（固定 keystore，见 .github/workflows/deploy.yml），
    // 这样私钥不需要经过 Gradle 配置，本地 assembleRelease 产出未签名包。

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

    testOptions {
        // 让 Robolectric 能真的把 Compose 画进 Bitmap（截图测试用）
        unitTests.all { it.systemProperty("robolectric.pixelCopyRenderMode", "hardware") }
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
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

    // 手写数字识别：bundled 版模型打在 APK 里，离线可用、不依赖 GMS
    implementation("com.google.mlkit:text-recognition:16.0.1")

    // 本地没有真机/模拟器，用 Robolectric 在 JVM 上真实组合每一屏做冒烟测试
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation(platform("androidx.compose:compose-bom:2024.10.01"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
