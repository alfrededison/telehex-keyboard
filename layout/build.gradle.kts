import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins { alias(libs.plugins.kotlin.multiplatform) }

// Bố cục, lớp phím, profile có sẵn và hình học lưới phím, dùng chung cho Android và iOS. Không phụ thuộc Telex.
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
