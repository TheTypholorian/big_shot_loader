rootProject.name = "big_shot_loader"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        mavenLocal()
        maven("https://maven.fabricmc.net")
    }
}

include("agent")
include("installer")
include("loader")