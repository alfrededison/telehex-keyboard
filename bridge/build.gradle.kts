import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins { alias(libs.plugins.kotlin.multiplatform) }

/**
 * Cầu nối Swift: gói `:telex` và `:layout` thành một framework `TeleHexCore` cho iOS (Kotlin/Native chỉ cho một framework
 * mỗi app, hai framework sẽ trùng runtime), cùng lớp chuyển dữ liệu bố cục sang định dạng iOS đang lưu.
 */
kotlin {
    jvm { compilerOptions.jvmTarget.set(JvmTarget.JVM_17) }
    if (System.getProperty("os.name") == "Mac OS X") {
        val xcframework = XCFramework("TeleHexCore")
        listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
            target.binaries.framework {
                baseName = "TeleHexCore"
                isStatic = true
                binaryOption("bundleId", "com.telehex.keyboard.core")
                export(project(":telex"))
                export(project(":typing"))
                xcframework.add(this)
            }
        }
    }
    sourceSets {
        commonMain.dependencies {
            api(project(":telex"))
            api(project(":typing"))
            // Chỉ lớp chuyển đổi ở đây ra Swift; GridSpec, Profile… của layout không lộ ra để không trùng tên kiểu Swift
            implementation(project(":layout"))
        }
        jvmTest {
            kotlin.srcDir("src/test/kotlin")
            dependencies { implementation(libs.junit) }
        }
    }
}

tasks.register("test") { dependsOn("jvmTest") }
