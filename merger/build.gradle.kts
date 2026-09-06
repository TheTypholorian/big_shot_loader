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
    implementation("org.ow2.asm:asm:9.10.1")
    implementation("org.ow2.asm:asm-tree:9.10.1")
    implementation("org.ow2.asm:asm-util:9.10.1")
    implementation("org.ow2.asm:asm-commons:9.10.1")
    implementation("net.typho:asm_util:${rootProject.property("versions.asm_util")}")
    implementation("net.typho:data_util:${rootProject.property("versions.data_util")}")
    implementation(project(":data")) // TODO
    implementation(kotlin("reflect"))
}

kotlin {
    jvmToolchain(8)
}