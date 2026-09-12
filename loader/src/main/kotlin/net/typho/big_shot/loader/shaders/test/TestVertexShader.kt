package net.typho.big_shot.loader.shaders.test

import net.typho.big_shot.loader.shaders.reflect.JavaShader
import org.joml.Vector3d
import org.joml.Vector3dc
import org.joml.Vector3f
import org.joml.Vector3fc
import kotlin.io.path.Path

class TestVertexShader : JavaShader.Vertex() {
    //@Import
    //val lib = TestLibrary()

    @Input
    @Location(0)
    @JvmField
    var pos: Vector3fc = Vector3f()
    @Input
    @Location(1)
    @JvmField
    var pos2: Vector3fc = Vector3f()

    @Output
    @Location(0)
    @JvmField
    var outPos: Vector3fc = Vector3f()

    override fun main() {
        var i = 0

        repeat(10) {
            i++
        }
    }
}

/*
class TestLibrary : JavaShader.Library() {
    fun add(a: Vector3fc, b: Vector3fc): Vector3f {
        return a.add(b, Vector3f())
    }
}
 */