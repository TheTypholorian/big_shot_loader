package net.typho.big_shot.loader.shaders.test

import net.typho.big_shot.loader.shaders.reflect.JavaShader
import org.joml.Vector3f
import org.joml.Vector3fc
import org.joml.Vector4f
import org.joml.Vector4fc

abstract class TestVertexShader : JavaShader.Vertex() {
    @get:Input
    @get:Location(0)
    abstract val pos: Vector3fc
    @get:Input
    @get:Location(1)
    abstract val pos2: Vector3fc

    @set:Output
    @set:Location(0)
    abstract var outPos: Vector3fc

    override fun main() {
        val temp = Vector3f()
        val temp2 = Vector3f(temp).sub(pos2)
        outPos = temp.add(pos)
        outPos = temp2
    }
}