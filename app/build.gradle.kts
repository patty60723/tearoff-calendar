plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.patty60723.tearoff"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.patty60723.tearoff"
        minSdk = 26
        targetSdk = 35
        // CI 每次打包的編號遞增，手機上才能直接覆蓋安裝更新
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "0.1.${versionCode}"
    }

    signingConfigs {
        // 固定的簽章，讓每次打包出來的 APK 都能覆蓋舊版，不用先解除安裝
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("cn.6tail:lunar:1.7.7")
    testImplementation("junit:junit:4.13.2")
    // 單元測試裡 Android 的 org.json 只是空殼，換成真的才能讀故事檔
    testImplementation("org.json:json:20240303")
}
