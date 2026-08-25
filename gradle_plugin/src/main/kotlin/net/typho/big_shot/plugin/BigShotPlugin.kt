package net.typho.big_shot.plugin

import net.typho.big_shot.data.TargetMinecraftVersion
import net.typho.big_shot.plugin.transform.MinecraftTransformAction
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.type.ArtifactTypeDefinition

class BigShotPlugin : Plugin<Project> {
    init {
        println("new plugin")
    }

    override fun apply(project: Project) {
        val version = TargetMinecraftVersion["26.2"]

        project.dependencies.registerTransform(MinecraftTransformAction::class.java) {
            it.from.attribute(
                ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE,
                ArtifactTypeDefinition.JAR_TYPE
            )
            it.to.attribute(
                ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE,
                "minecraft-jar"
            )
        }

        val minecraft = project.configurations.create("minecraft") {
            it.isCanBeResolved = true
            it.isCanBeConsumed = false

            it.attributes.attribute(
                ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE,
                "minecraft-jar"
            )
        }

        project.dependencies.add("implementation", minecraft.incoming.artifactView {}.files)

        project.repositories.maven {
            it.setUrl("https://libraries.minecraft.net")
        }

        for (lib in version.info.libraries) {
            project.dependencies.add("implementation", lib.name)
        }
    }
}