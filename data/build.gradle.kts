plugins {
    kotlin("jvm") version "2.4.0"
}

group = "net.typho"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://typho.net/maven")
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("net.typho:data_util:${rootProject.property("versions.data_util")}")
    implementation("org.semver4j:semver4j:6.0.0")
}

kotlin {
    jvmToolchain(8)
}