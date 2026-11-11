import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins { alias(libs.plugins.kotlin.multiplatform) }

val generateGoldenFixture = tasks.register("generateGoldenFixture") {
    val source = layout.projectDirectory.file("src/test/resources/golden.tsv").asFile
    val output = layout.buildDirectory.dir("generated/commonTest").get().asFile
    inputs.file(source)
    outputs.dir(output)
    doLast {
        val escaped = source.readText().replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\$", "\\\$").replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t")
        val target = output.resolve("com/telehex/portable/GoldenFixture.kt")
        target.parentFile.mkdirs()
        target.writeText("package com.telehex.portable\ninternal val goldenFixture = \"" + escaped + "\"\n")
    }
}

kotlin {
    jvm { compilerOptions.jvmTarget.set(JvmTarget.JVM_17) }
    if (System.getProperty("os.name") == "Mac OS X") {
        macosArm64()
        val xcframework = XCFramework("TeleHexCore")
        listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
            target.binaries.framework {
                baseName = "TeleHexCore"
                isStatic = true
                binaryOption("bundleId", "com.telehex.keyboard.core")
                xcframework.add(this)
            }
        }
    }
    sourceSets {
        commonTest {
            kotlin.srcDir(generateGoldenFixture)
            dependencies { implementation(kotlin("test")) }
        }
        jvmTest {
            kotlin.srcDir("src/test/kotlin")
            resources.srcDir("src/test/resources")
            resources.srcDir("../app/src/main/assets")
            dependencies { implementation(libs.junit) }
        }
    }
}

// Preserve the command used by Android CI and release scripts.
tasks.register("test") { dependsOn("jvmTest") }
