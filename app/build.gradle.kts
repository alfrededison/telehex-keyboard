import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Phiên bản: chỉ sửa ở dòng appVersion (X.Y.Z). tools/version.sh đọc đúng dòng này.
val appVersion = "0.5.1"

// Mặc định là số của bản release (X.Y.Z, code = X·10^7 + Y·10^5 + Z·10^3 + 999) để build từ tag
// ở đâu cũng ra cùng kết quả. Bản dev do tools/release-build.sh đặt VERSION_NAME/VERSION_CODE.
val releaseVersionCode = appVersion.split('.').map { it.toInt() }.let { (x, y, z) -> x * 10_000_000 + y * 100_000 + z * 1_000 + 999 }
val resolvedVersionName = System.getenv("VERSION_NAME")?.takeIf { it.isNotEmpty() } ?: appVersion
val resolvedVersionCode = System.getenv("VERSION_CODE")?.takeIf { it.isNotEmpty() }?.let {
    it.toIntOrNull() ?: error("VERSION_CODE must be an integer, got '$it'")
} ?: releaseVersionCode

// Ký bằng key dự án khi tools/release-build.sh đã giải mã keystore ra file tạm.
val projectKeystore: String? = System.getenv("ANDROID_KEYSTORE_PATH")?.takeIf { it.isNotEmpty() }

android {
    namespace = "com.telehex.keyboard"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.telehex.keyboard"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = resolvedVersionCode
        versionName = resolvedVersionName
    }

    signingConfigs {
        if (projectKeystore != null) {
            create("project") {
                storeFile = file(projectKeystore)
                storeType = "pkcs12"
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (projectKeystore != null) signingConfig = signingConfigs.getByName("project")
        }
    }

    androidResources {
        noCompress += "ngram" // để mmap được mô hình n-gram trong assets
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets["main"].kotlin.srcDirs("src/main/kotlin")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":telex"))
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
}
