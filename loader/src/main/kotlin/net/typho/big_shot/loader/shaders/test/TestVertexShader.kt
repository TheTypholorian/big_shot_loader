package net.typho.big_shot.loader.shaders.test

import net.typho.big_shot.loader.shaders.reflect.JavaShader

abstract class TestVertexShader : JavaShader.Vertex() {
    @get:Input
    abstract val a: Int
    @get:Input
    abstract val b: Int

    @set:Output
    abstract var output: Int

    override fun main() {
        val arr = IntArray(30)
        arr[a] = b
        output = arr[a]
    }
}