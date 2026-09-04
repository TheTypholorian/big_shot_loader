package net.typho.big_shot.loader.shaders.reflect

sealed class JavaShader {
    @Target(AnnotationTarget.FIELD)
    annotation class Import(
        val name: String = ""
    )

    @Target(AnnotationTarget.FIELD)
    annotation class Input(
        val name: String = ""
    )

    @Target(AnnotationTarget.FIELD)
    annotation class Output(
        val name: String = ""
    )

    @Target(AnnotationTarget.FIELD)
    annotation class Uniform(
        val name: String = ""
    )

    @Target(AnnotationTarget.FIELD)
    annotation class Location(
        val value: Int
    )

    abstract class Vertex : JavaShader() {
        abstract fun main()
    }

    abstract class Fragment : JavaShader() {
        abstract fun main()
    }

    abstract class Geometry : JavaShader() {
        abstract fun main()
    }

    abstract class Compute : JavaShader() {
        abstract fun main()
    }

    abstract class Library : JavaShader()

    // TODO tess shaders
}