package net.typho.big_shot.loader.shaders.reflect

import net.typho.asm_util.ASMUtil.forEach
import net.typho.asm_util.method.MethodPointer
import net.typho.big_shot.loader.shaders.bytecode.*
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import java.nio.ByteBuffer

class JavaShaderCompiler(
    @JvmField
    val node: ClassNode
) : JavaShaderTypeHandler.Supplier {
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
    val typeHandlers = mutableListOf(JomlVectorTypeHandler, KotlinIntrinsicsTypeHandler)
    @JvmField
    val variables = mutableMapOf<String, ShaderVariable>()

    override fun getTypeHandler(type: Type) = typeHandlers.firstNotNullOfOrNull { it.getTypeHandler(type) }

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
        val shaderType = ShaderBytecodeType.convertJavaType(type)

        return ShaderVariable(ShaderBytecodeType.Pointer(storageClass, shaderType), restricted = shaderType is ShaderBytecodeType.Vector, javaType = type, label = ShaderLabelNode(name), location = location)
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