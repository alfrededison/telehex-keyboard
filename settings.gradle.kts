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
// Native-only builds do not require an Android SDK installation.
if (providers.gradleProperty("skipAndroid").orNull != "true") include(":app")
