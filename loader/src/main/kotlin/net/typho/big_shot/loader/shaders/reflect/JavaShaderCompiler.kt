package net.typho.big_shot.loader.shaders.reflect

import net.typho.asm_util.ASMUtil.forEach
import net.typho.asm_util.method.MethodPointer
import net.typho.big_shot.loader.shaders.bytecode.*
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import java.nio.ByteBuffer
import java.util.function.Function

class JavaShaderCompiler(
    @JvmField
    val node: ClassNode
) : JavaShaderClassHandler.Supplier {
    @JvmField
    val builder = ShaderBytecodeBuilder(
        when (node.superName) {
            $$"net/typho/big_shot/loader/shaders/reflect/JavaShader$Vertex" -> EXEC_MODEL_VERTEX
            $$"net/typho/big_shot/loader/shaders/reflect/JavaShader$Fragment" -> EXEC_MODEL_FRAGMENT
            $$"net/typho/big_shot/loader/shaders/reflect/JavaShader$Geometry" -> EXEC_MODEL_GEOMETRY
            $$"net/typho/big_shot/loader/shaders/reflect/JavaShader$Compute" -> EXEC_MODEL_GL_COMPUTE
            else -> throw IllegalStateException("${node.name} does not directly extend any JavaShader type")
        }
    )
    @JvmField
    val classHandlers = mutableListOf(VectorClassHandler, KotlinIntrinsicsClassHandler)
    @JvmField
    val variables = mutableMapOf<String, ShaderVariable>()

    override fun getClassHandler(className: String) = classHandlers.firstNotNullOfOrNull { it.getClassHandler(className) }

    fun compileMethod(node: MethodNode): ShaderFunction {
        val compiler = JavaShaderMethodCompiler(this, node)
        compiler.compile()
        return compiler.function
    }

    fun compileField(node: FieldNode): ShaderVariable? {
        var storageClass: Int? = null
        var name = node.name
        var type: Type? = null
        var location: Int? = null

        node.visibleAnnotations?.forEach { anno ->
            when (anno.desc) {
                $$"Lnet/typho/big_shot/loader/shaders/reflect/JavaShader$Input;" -> {
                    storageClass = STORAGE_CLASS_INPUT
                    type = Type.getType(node.desc)
                    anno.forEach { key, value -> if (key == "name" && (value as String).isNotEmpty()) name = value }
                }
                $$"Lnet/typho/big_shot/loader/shaders/reflect/JavaShader$Output;" -> {
                    storageClass = STORAGE_CLASS_OUTPUT
                    type = Type.getType(node.desc)
                    anno.forEach { key, value -> if (key == "name" && (value as String).isNotEmpty()) name = value }
                }
                $$"Lnet/typho/big_shot/loader/shaders/reflect/JavaShader$Uniform;" -> {
                    storageClass = STORAGE_CLASS_UNIFORM
                    type = Type.getType(node.desc)
                    anno.forEach { key, value -> if (key == "name" && (value as String).isNotEmpty()) name = value }
                }
                $$"Lnet/typho/big_shot/loader/shaders/reflect/JavaShader$Location;" -> {
                    anno.forEach { key, value -> if (key == "value") location = value as Int }
                }
            }
        }

        storageClass ?: return null
        type ?: return null

        return ShaderVariable(ShaderBytecodeType.Pointer(storageClass, ShaderBytecodeType.convertJavaType(type)), javaType = type, label = ShaderLabelNode(name), location = location)
    }

    fun compile(): ByteBuffer {
        builder.capabilities.add(CAP_SHADER)
        builder.import("GLSL.std.450") // TODO

        //val ktMeta = node.visibleAnnotations?.firstOrNull { it.desc == "Lkotlin/Metadata;" }?.kotlinMetadata?.let { KotlinClassMetadata.readLenient(it) }

        node.fields.mapNotNullTo(builder.variables) {
            val field = compileField(it)

            if (field != null) {
                variables[it.name] = field
            }

            field
        }

        val main = compileMethod(MethodPointer.method().name("main").desc("()V").findOrThrow(node))
        builder.functions.add(main)

        return builder.build(main)
    }
}