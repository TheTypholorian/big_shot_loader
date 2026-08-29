package net.typho.big_shot.loader.shaders.test

import net.typho.big_shot.loader.shaders.reflect.JavaShader

abstract class TestVertexShader : JavaShader.Vertex() {
    @get:Input
    abstract val testInput: Float
    @get:Input
    abstract val testInput2: Float

    @set:Output
    abstract var testOutput: Float

    override fun main() {
        var a = testInput
        a *= testInput2
        testOutput = -a
    }
}