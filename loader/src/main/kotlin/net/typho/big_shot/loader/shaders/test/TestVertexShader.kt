package net.typho.big_shot.loader.shaders.test

import net.typho.big_shot.loader.shaders.reflect.JavaShader
import org.joml.Vector3f
import org.joml.Vector3fc
import org.joml.Vector4f
import org.joml.Vector4fc

abstract class TestVertexShader : JavaShader.Vertex() {
    @get:Input
    @get:Location(0)
    abstract val pos: Vector4fc
    @get:Input
    @get:Location(1)
    abstract val pos2: Vector4fc

    @set:Output
    @set:Location(0)
    abstract var outPos: Vector4fc

    override fun main() {
        val a = 2f
        val temp = Vector4f()
        outPos = Vector4f(1f, a, 3f, 10f).add(pos, temp)
    }
}