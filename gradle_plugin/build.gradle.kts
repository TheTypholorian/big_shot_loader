plugins {
    kotlin("jvm") version "2.4.0"
    `java-gradle-plugin`
    `maven-publish`
}

group = "net.typho.big_shot"
version = "1.0.0"

repositories {
    mavenCentral()
    gradlePluginPortal()
    maven("https://maven.fabricmc.net")
    maven("https://typho.net/maven")
}

dependencies {
    implementation("org.ow2.asm:asm:9.10.1")
    implementation("org.ow2.asm:asm-tree:9.10.1")
    implementation("org.ow2.asm:asm-util:9.10.1")
    implementation("org.ow2.asm:asm-commons:9.10.1")
    implementation("net.typho:asm_util:1.1.1")
    implementation(project(":data"))
}

gradlePlugin {
    plugins {
        create("big_shot_plugin") {
            id = "net.typho.big_shot.plugin"
            implementationClass = "net.typho.big_shot.plugin.BigShotPlugin"
        }
    }
}