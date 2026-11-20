pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "telehex-keyboard"
include(":telex")
include(":layout")
include(":bridge")
// Native-only builds do not require an Android SDK installation.
if (providers.gradleProperty("skipAndroid").orNull != "true") include(":app")
