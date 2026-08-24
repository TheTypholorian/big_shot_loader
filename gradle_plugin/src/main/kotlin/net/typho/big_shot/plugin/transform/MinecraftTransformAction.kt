package net.typho.big_shot.plugin.transform

import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity

abstract class MinecraftTransformAction : TransformAction<MinecraftTransformAction.Parameters> {
    @get:InputArtifact
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val input: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val input = input.get().asFile
        val output = outputs.file(input.nameWithoutExtension + "-transformed.jar")
        println("Transforming $input")
        input.copyTo(output, overwrite = true) // TODO
    }

    interface Parameters : TransformParameters
}