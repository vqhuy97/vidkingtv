plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android") // Thêm dòng này nếu dự án của bạn sử dụng ngôn ngữ Kotlin
}

android {
    namespace = "com.vidkingtv.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.vidkingtv.app"
        minSdk = 21       // Hãy điều chỉnh lại theo dự án thực tế của bạn
        targetSdk = 34    // Hãy điều chỉnh lại theo dự án thực tế của bạn
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    // Nếu bạn đang dùng Java 17 (như trong log của setup-java)
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    // Nếu dùng Kotlin, cần thêm khối này
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Khai báo các thư viện của bạn ở đây. Ví dụ:
    // implementation("androidx.core:core-ktx:1.12.0")
    // implementation("androidx.appcompat:appcompat:1.6.1")
}
