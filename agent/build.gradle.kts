plugins {
    kotlin("jvm") version "2.4.0"
    id("com.gradleup.shadow") version "9.2.0"
}

group = "net.typho.big_shot_loader"
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
    implementation(kotlin("stdlib"))

    compileOnly("org.ow2.asm:asm:9.10.1")
    compileOnly("org.ow2.asm:asm-tree:9.10.1")
    compileOnly("org.ow2.asm:asm-util:9.10.1")
    compileOnly("org.ow2.asm:asm-commons:9.10.1")
    compileOnly("org.jetbrains:annotations:26.0.2")
    compileOnly("org.spongepowered:mixin:0.8.5")
    implementation("net.typho:asm_util:1.0.3")
}

kotlin {
    jvmToolchain(8)
}

tasks.shadowJar {
    archiveClassifier.set("")

    manifest {
        attributes(
            "Premain-Class" to "net.typho.big_shot.agent.BigShotAgent",
            "Can-Redefine-Classes" to "true",
            "Can-Retransform-Classes" to "true",
            "Can-Set-Native-Method-Prefix" to "true"
        )
    }
}