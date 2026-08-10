plugins {
    kotlin("jvm") version "2.4.0"
    id("com.gradleup.shadow") version "9.2.0"
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

val jij = configurations.create("jij")

dependencies {
    minecraft("com.mojang:minecraft:26.2") // TODO remove this?

    jij(kotlin("stdlib"))

    compileOnly("org.ow2.asm:asm:9.10.1")
    compileOnly("org.ow2.asm:asm-tree:9.10.1")
    compileOnly("org.ow2.asm:asm-util:9.10.1")
    compileOnly("org.ow2.asm:asm-commons:9.10.1")
    compileOnly("org.jetbrains:annotations:26.0.2")
    compileOnly("org.spongepowered:mixin:0.8.5")
    jij(implementation("net.typho:asm_util:1.0.11")!!)
}

kotlin {
    jvmToolchain(8)
}

tasks.processResources {
    dependsOn(project(":loader").tasks.shadowJar)
}

tasks.shadowJar {
    archiveClassifier.set("")
    configurations = listOf(jij)

    manifest {
        attributes(
            "Premain-Class" to "net.typho.big_shot.agent.BigShotAgent",
            "Can-Redefine-Classes" to "true",
            "Can-Retransform-Classes" to "true",
            "Can-Set-Native-Method-Prefix" to "true"
        )
    }
}