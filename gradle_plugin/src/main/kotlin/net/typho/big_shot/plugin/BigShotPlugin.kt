package net.typho.big_shot.plugin

import net.typho.big_shot.plugin.transform.MinecraftTransformAction
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.type.ArtifactTypeDefinition

class BigShotPlugin : Plugin<Project> {
    override fun apply(project: Project) {
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

        project.repositories.ivy {
            it.patternLayout {
                it.
            }
        }

        project.dependencies.add("implementation", minecraft.incoming.artifactView {}.files)
    }
}