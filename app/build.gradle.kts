plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "cn.sanchuang.jianqu"
    compileSdk = 35

    defaultConfig {
        applicationId = "cn.sanchuang.jianqu"
        minSdk = 30
        targetSdk = 33
        versionCode = 4
        versionName = "0.2.2-preview"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
