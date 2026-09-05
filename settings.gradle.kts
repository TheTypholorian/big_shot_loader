rootProject.name = "big_shot_loader"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        mavenLocal()
        maven("https://maven.fabricmc.net")
        maven("https://typho.net/maven")
    }
}

include("agent")
include("data")
include("decompiler")
includeBuild("gradle_plugin")
include("installer")
include("loader")
include("merger")
include("test_mod")