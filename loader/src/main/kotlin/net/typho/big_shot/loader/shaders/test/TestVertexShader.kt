package net.typho.big_shot.loader.shaders.test

import net.typho.big_shot.loader.shaders.reflect.JavaShader

abstract class TestVertexShader : JavaShader.Vertex() {
    @get:Input
    @get:Location(0)
    abstract val testInput: Int

    @set:Output
    @set:Location(0)
    abstract var testOutput: Int

    override fun main() {
        testOutput = testInput ushr 2
    }
}