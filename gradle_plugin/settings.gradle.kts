rootProject.name = "gradle_plugin"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        mavenLocal()
        maven("https://maven.fabricmc.net")
    }
}