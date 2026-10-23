import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.services)
}

val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("keystore.properties")
if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}
val hasReleaseKeystore = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
    .all { !keystoreProperties.getProperty(it).isNullOrBlank() }

android {
    namespace = "com.aujunpeak.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.aujunpeak.app"
        minSdk = 26
        targetSdk = 29
        versionCode = 17
        versionName = "17.0"

        // ── Nhúng SHA-256 fingerprint chữ ký vào BuildConfig để kiểm tra tại runtime ──
        // Thay giá trị dưới đây bằng SHA-256 của keystore RELEASE của bạn (uppercase, không dấu ':')
        // Lấy bằng lệnh:  keytool -list -v -keystore release.jks -alias YOUR_ALIAS
        buildConfigField("String", "RELEASE_CERT_SHA256",
            "\"2B36102354D0043FC26F526A15195B05059D7EF7A3F505B64379949A88F1315D\"")
    }

    signingConfigs {
        create("release") {
            if (hasReleaseKeystore) {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }


    buildTypes {

        // ── DEBUG: không obfuscate, bật log ────────────────────────────────────
        debug {
            isMinifyEnabled   = false
            isShrinkResources = false
            isDebuggable      = true
        }

        // ── RELEASE: bật tất cả lớp bảo vệ ─────────────────────────────────────
        release {
            // Bật R8 full-mode — mã hóa, đổi tên, xóa code không dùng
            isMinifyEnabled   = true

            // Xóa resource không dùng khỏi APK (ảnh, layout, string…)
            isShrinkResources = true

            // Không cho phép debug trong bản release
            isDebuggable      = false

            // R8 full mode: mạnh hơn ProGuard tiêu chuẩn
            // (tự động bật khi isMinifyEnabled=true với AGP 8+)

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // Bật nếu đã tạo signingConfig release ở trên
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    // ── Tắt ZIP alignment check — đã được aapt2 xử lý ──────────────────────
    // Bật packaging options để loại bỏ file metadata dễ bị reverse engineer
    packaging {
        resources {
            excludes += setOf(
                "META-INF/*.kotlin_module",
                "META-INF/MANIFEST.MF",
                "META-INF/*.SF",
                "META-INF/*.DSA",
                "META-INF/*.RSA",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/DEPENDENCIES",
                "kotlin/**",
                "**/*.proto",
                "**/*.bin",
                "DebugProbesKt.bin"
            )
        }
        // Không extract native libs ra ngoài APK — khó hook hơn
        jniLibs {
            useLegacyPackaging = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
        // Tắt coroutine debug metadata trong release
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-Xno-call-assertions",
            "-Xno-receiver-assertions",
            "-Xno-param-assertions"
        )
    }

    buildFeatures {
        viewBinding  = true
        buildConfig  = true
        // Tắt các feature không dùng để giảm attack surface
        dataBinding  = false
        mlModelBinding = false
        renderScript = false
        shaders      = false
    }

    // Tắt lint stop-on-error để build không bị chặn bởi warning
    lint {
        abortOnError = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.glide)
    implementation("com.airbnb.android:lottie:6.6.7")

    // Google Sign-In
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    // WorkManager for background heartbeat
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Lifecycle (for lifecycleScope in fragments)
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    // Firebase BOM
    implementation(platform("com.google.firebase:firebase-bom:33.15.0"))
    implementation("com.google.firebase:firebase-messaging-ktx")

    // Kotlinx Coroutines Play Services
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
}
