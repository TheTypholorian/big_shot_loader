package net.typho.big_shot.loader.shaders.test

import net.typho.big_shot.loader.shaders.reflect.JavaShader

abstract class TestVertexShader : JavaShader.Vertex() {
    @get:Input
    abstract val testInput: Float
    @get:Input
    abstract val testInput2: Float
    @get:Input
    abstract val testInput3: Long

    @set:Output
    abstract var testOutput: Float

    override fun main() {
        if (testInput3 >= 4L) {
            testOutput += 1
        } else {
            testOutput -= 1
        }
    }
}