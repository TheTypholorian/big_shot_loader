plugins {
    kotlin("jvm") version "2.4.0"
    id("net.typho.big_shot.plugin") version "1.0.0"
}

group = "net.typho"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://typho.net/maven")
}

dependencies {
    implementation(project(":loader"))
}

kotlin {
    jvmToolchain(25)
}