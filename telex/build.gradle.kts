import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation(libs.junit)
}

// Test dữ liệu ứng dụng mà không đưa dữ liệu đó vào artifact/runtime của module thuật toán.
tasks.processTestResources {
    from("../app/src/main/assets") {
        include("en-frequency.txt")
    }
}
