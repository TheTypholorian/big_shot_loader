package net.typho.big_shot.loader.shaders.test

import net.typho.big_shot.loader.math.IVec3
import net.typho.big_shot.loader.math.IVec4
import net.typho.big_shot.loader.shaders.reflect.JavaShader

abstract class TestVertexShader : JavaShader.Vertex() {
    @get:Input
    abstract val pos: IVec3<Float>
    @get:Input
    abstract val color: IVec4<Int>

    @set:Output
    abstract var outPos: IVec3<Float>
    @set:Output
    abstract var outColor: IVec4<Float>

    override fun main() {
        outPos = pos
        //outColor = color.toFloat() / 255f
    }
}