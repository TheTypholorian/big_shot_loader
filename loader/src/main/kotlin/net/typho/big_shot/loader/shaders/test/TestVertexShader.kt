package net.typho.big_shot.loader.shaders.test

import net.typho.big_shot.loader.shaders.reflect.JavaShader

abstract class TestVertexShader : JavaShader.Vertex() {
    @get:Input
    @get:Location(0)
    abstract val testInput: Float

    @set:Output
    @set:Location(0)
    abstract var testOutput: Float

    override fun main() {
        testOutput = testInput * 2 - 0.5f
    }
}