plugins {
    kotlin("jvm")
    id("com.gradleup.shadow") version "9.2.0"
}

group = "net.typho.big_shot_loader"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.xerial:sqlite-jdbc:3.50.3.0")
    implementation("com.google.code.gson:gson:2.14.0")
}

kotlin {
    jvmToolchain(8)
}

tasks.shadowJar {
    archiveClassifier.set("")

    manifest {
        attributes(
            "Main-Class" to "net.typho.big_shot.installer.BigShotInstaller"
        )
    }
}