plugins {
    kotlin("jvm") version "2.4.0"
    id("net.typho.big_shot.plugin") version "1.0.0"
}

group = "net.typho.big_shot"
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

tasks.register("debugMinecraftAttributes") {
    doLast {
        configurations.getByName("minecraft").let { configuration ->
            println("CONFIGURATION: ${configuration.name}")
            println("ATTRIBUTES: ${configuration.attributes}")

            configuration.incoming.resolutionResult.allComponents.forEach { component ->
                println()
                println("COMPONENT: ${component.id}")

                component.variants.forEach { variant ->
                    println("  VARIANT: ${variant.displayName}")
                    println("  ATTRIBUTES: ${variant.attributes}")
                }
            }
        }
        configurations.getByName("compileClasspath").let { configuration ->
            println("CONFIGURATION: ${configuration.name}")
            println("ATTRIBUTES: ${configuration.attributes}")

            configuration.incoming.resolutionResult.allComponents.forEach { component ->
                println()
                println("COMPONENT: ${component.id}")

                component.variants.forEach { variant ->
                    println("  VARIANT: ${variant.displayName}")
                    println("  ATTRIBUTES: ${variant.attributes}")
                }
            }
        }
    }
}