package net.typho.big_shot.plugin

import net.typho.big_shot.plugin.transform.MinecraftTransformAction
import org.apache.maven.model.Model
import org.apache.maven.model.io.xpp3.MavenXpp3Writer
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.installation.InstallRequest
import org.eclipse.aether.repository.LocalArtifactRequest
import org.eclipse.aether.repository.LocalRepository
import org.eclipse.aether.supplier.RepositorySystemSupplier
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Attribute
import org.gradle.api.plugins.JavaPluginExtension
import java.net.URI
import java.nio.file.Files
import kotlin.io.path.writeBytes
import kotlin.io.path.writer

class BigShotPlugin : Plugin<Project> {
    companion object {
        @JvmField
        val MINECRAFT_ATTRIBUTE = Attribute.of(
            "big_shot.minecraft",
            Boolean::class.javaObjectType
        )
    }

    override fun apply(project: Project) {
        val cacheFolder = project.gradle.gradleUserHomeDir.resolve("caches").resolve("big_shot")
        val service = project.gradle.sharedServices.registerIfAbsent("BigShot", BigShotBuildService::class.java) {
            it.parameters.cacheFolder.set(cacheFolder)
        }

        project.plugins.apply("java")
        val javaExt = project.extensions.getByType(JavaPluginExtension::class.java)
        val manifest = javaExt.sourceSets.create("manifest")

        project.dependencies.artifactTypes.configureEach { it.attributes.attribute(MINECRAFT_ATTRIBUTE, false) }
        project.dependencies.registerTransform(MinecraftTransformAction::class.java) {
            it.from.attribute(MINECRAFT_ATTRIBUTE, false)
            it.to.attribute(MINECRAFT_ATTRIBUTE, true)
        }

        val minecraft = project.configurations.create("minecraft")
        project.dependencies.add("implementation", minecraft.incoming.artifactView { it.attributes.attribute(MINECRAFT_ATTRIBUTE, true) }.artifacts.artifactFiles)

        val repoPath = cacheFolder.resolve("minecraft_repo").toPath()
        val repoSystem = RepositorySystemSupplier().get()
        val repoSession = repoSystem.createSessionBuilder().withLocalRepositories(LocalRepository(repoPath)).build()

        project.repositories.maven {
            it.setUrl(repoPath)
        }
        project.repositories.maven {
            it.setUrl("https://libraries.minecraft.net")
        }

        val version = service.get().versionManifest.getFamily("26.2")

        val artifact = DefaultArtifact(
            "com.mojang",
            "minecraft",
            "jar",
            version.primaryVersion
        )

        if (!repoSession.localRepositoryManager.find(repoSession, LocalArtifactRequest().setArtifact(artifact)).isAvailable) {
            val temp = Files.createTempDirectory("big_shot_minecraft_download")
            val tempJar = temp.resolve("client.jar")
            val tempPom = temp.resolve("client.pom")

            tempJar.writeBytes(URI.create(version.info.downloads.client.url).toURL().openConnection().getInputStream().readAllBytes())

            val model = Model()

            model.modelVersion = "4.0.0"
            model.groupId = artifact.groupId
            model.artifactId = artifact.artifactId
            model.version = artifact.version
            model.packaging = "jar"

            tempPom.writer().use {
                MavenXpp3Writer().write(it, model)
            }

            repoSystem.install(
                repoSession,
                InstallRequest()
                    .addArtifact(artifact.setPath(tempJar))
                    .addArtifact(DefaultArtifact(
                        "com.mojang",
                        "minecraft",
                        "pom",
                        version.primaryVersion
                    ).setPath(tempPom))
            )
        }

        project.dependencies.add("minecraft", "com.mojang:minecraft:${version.primaryVersion}")

        for (lib in version.info.libraries) {
            project.dependencies.add("implementation", lib.name)
        }
    }
}