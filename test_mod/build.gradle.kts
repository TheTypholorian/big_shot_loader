plugins {
    kotlin("jvm") version "2.4.0"
    id("net.fabricmc.fabric-loom") version "1.16-SNAPSHOT"
}

group = "net.typho.big_shot"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.spongepowered.org/repository/maven-public/")
    maven("https://typho.net/maven")
}

dependencies {
    minecraft("com.mojang:minecraft:26.2")
    compileOnly("net.fabricmc:fabric-loader:0.19.3")

    implementation(kotlin("stdlib"))
    implementation(project(":loader"))

    implementation("org.ow2.asm:asm:9.10.1")
    implementation("org.ow2.asm:asm-tree:9.10.1")
    implementation("org.ow2.asm:asm-util:9.10.1")
    implementation("org.ow2.asm:asm-commons:9.10.1")
    implementation("org.jetbrains:annotations:26.0.2")
    implementation("org.spongepowered:mixin:0.8.5")
    implementation("net.typho:asm_util:1.1.1")
}

kotlin {
    jvmToolchain(25)
}

tasks.jar {
    archiveVersion.set("")
    destinationDirectory.set(File("${System.getenv("appdata")}/ModrinthApp/profiles/big shot testing/big_shot_mods"))
}