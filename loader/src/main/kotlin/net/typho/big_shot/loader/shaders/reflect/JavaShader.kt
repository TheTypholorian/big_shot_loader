package net.typho.big_shot.loader.shaders.reflect

sealed class JavaShader {
    abstract fun main()

    @Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
    annotation class Input(
        val name: String = ""
    )

    @Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_SETTER)
    annotation class Output(
        val name: String = ""
    )

    @Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
    annotation class Uniform(
        val name: String = ""
    )

    @Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
    annotation class Location(
        val value: Int
    )

    abstract class Vertex : JavaShader()

    abstract class Fragment : JavaShader()

    abstract class Geometry : JavaShader()

    abstract class Compute : JavaShader()

    // TODO tess shaders
}