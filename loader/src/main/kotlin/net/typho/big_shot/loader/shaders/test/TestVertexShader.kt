package net.typho.big_shot.loader.shaders.test

import net.typho.big_shot.loader.shaders.reflect.JavaShader

abstract class TestVertexShader : JavaShader.Vertex() {
    @get:Input
    abstract val testInput: Float
    @get:Input
    abstract val testInput2: Float
    @get:Input
    abstract val testInput3: Float

    @set:Output
    abstract var testOutput: Float

    override fun main() {
        var a = testInput
        a *= testInput2
        a += testInput3 * 30
        testOutput = -a
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val test = object : TestVertexShader() {
                override val testInput: Float = 2f
                override val testInput2: Float = 4f
                override val testInput3: Float = 3f
                override var testOutput: Float = 0f
            }
            test.main()
            println(test.testOutput)
        }
    }
}