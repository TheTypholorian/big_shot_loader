package net.typho.big_shot.loader.shaders.test

import net.typho.big_shot.loader.shaders.reflect.JavaShader
import org.joml.Vector3f
import org.joml.Vector3fc

abstract class TestVertexShader : JavaShader.Vertex() {
    @get:Input
    @get:Location(0)
    abstract val pos: Vector3fc

    @set:Output
    @set:Location(0)
    abstract var outPos: Vector3fc

    override fun main() {
        val temp = Vector3f()
        pos.add(1f, 0.5f, 0.25f, temp)
        outPos = temp
    }
}