package net.typho.big_shot.plugin

import net.typho.big_shot.data.MinecraftVersionManifest
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.tasks.InputDirectory

abstract class BigShotBuildService : BuildService<BigShotBuildService.Parameters> {
    @JvmField
    var versionManifest = MinecraftVersionManifest.fromCache(parameters.cacheFolder.get().asFile)

    fun requireVersionIsKnown(version: String) {
        if (!versionManifest.versions.any { it.id == version }) {
            versionManifest = versionManifest.redownload()

            if (!versionManifest.versions.any { it.id == version }) {
                throw IllegalStateException("Redownloaded Minecraft version manifest yet version '$version' is still missing")
            }
        }
    }

    interface Parameters : BuildServiceParameters {
        @get:InputDirectory
        val cacheFolder: DirectoryProperty
    }
}