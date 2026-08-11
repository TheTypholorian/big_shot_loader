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
    ivy("https://github.com/TheTypholorian/asm_util/releases/download") {
        patternLayout {
            artifact("[revision]/[artifact]-[revision](-[classifier]).[ext]")
        }

        metadataSources {
            artifact()
        }
    }
}

dependencies {
    implementation("org.ow2.asm:asm:9.10.1")
    implementation("org.ow2.asm:asm-tree:9.10.1")
    implementation("org.ow2.asm:asm-util:9.10.1")
    implementation("org.ow2.asm:asm-commons:9.10.1")
    implementation("net.typho:asm_util:1.0.12")
}

gradlePlugin {
    plugins {
        create("big_shot_plugin") {
            id = "net.typho.big_shot.plugin"
            implementationClass = "net.typho.big_shot.plugin.BigShotPlugin"
        }
    }
}