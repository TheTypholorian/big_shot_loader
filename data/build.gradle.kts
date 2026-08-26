plugins {
    kotlin("jvm")
}

group = "net.typho.big_shot"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://typho.net/maven")
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("net.typho:data_util:1.2.6")
}

kotlin {
    jvmToolchain(8)
}