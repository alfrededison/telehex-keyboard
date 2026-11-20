import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins { alias(libs.plugins.kotlin.multiplatform) }

// Quy tắc gõ dùng chung cho Android và iOS (chèn gợi ý, dấu câu, gõ tắt, viết hoa…). Không phụ thuộc Telex hay bố cục.
kotlin {
    jvm { compilerOptions.jvmTarget.set(JvmTarget.JVM_17) }
    if (System.getProperty("os.name") == "Mac OS X") {
        macosArm64()
        iosArm64()
        iosSimulatorArm64()
    }
    sourceSets {
        commonTest { dependencies { implementation(kotlin("test")) } }
        jvmTest {
            kotlin.srcDir("src/test/kotlin")
            dependencies { implementation(libs.junit) }
        }
    }
}

tasks.register("test") { dependsOn("jvmTest") }
