plugins {
    kotlin("jvm") version "2.4.0"
    id("net.fabricmc.fabric-loom") version "1.16-SNAPSHOT"
}

group = "net.typho.big_shot"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.spongepowered.org/repository/maven-public/")
    ivy("https://github.com/TheTypholorian/asm_util/releases/download") {
        patternLayout {
            artifact("[revision]/[artifact]-[revision](-[classifier]).[ext]")
        }

        metadataSources {
            artifact()
        }

        content {
            includeGroup("net.typho")
        }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:26.2")
    compileOnly("net.fabricmc:fabric-loader:0.19.3")

    implementation(kotlin("stdlib"))

    compileOnly("org.ow2.asm:asm:9.10.1")
    compileOnly("org.ow2.asm:asm-tree:9.10.1")
    compileOnly("org.ow2.asm:asm-util:9.10.1")
    compileOnly("org.ow2.asm:asm-commons:9.10.1")
    compileOnly("org.jetbrains:annotations:26.0.2")
    compileOnly("org.spongepowered:mixin:0.8.5")
    implementation("net.typho:asm_util:1.0.11")
}

kotlin {
    jvmToolchain(25)
}

tasks.jar {
    archiveVersion.set("")
    destinationDirectory.set(project(":agent").file("src/main/resources"))
}