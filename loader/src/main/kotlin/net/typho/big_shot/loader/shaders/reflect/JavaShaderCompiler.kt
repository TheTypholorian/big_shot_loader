package net.typho.big_shot.loader.shaders.reflect

import net.typho.asm_util.ASMUtil.forEach
import net.typho.asm_util.method.MethodPointer
import net.typho.big_shot.loader.shaders.bytecode.*
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import java.nio.ByteBuffer

object JavaShaderCompiler {
    @JvmStatic
    fun convertType(type: Type): ShaderBytecodeType {
        return when (type.sort) {
            Type.VOID -> ShaderBytecodeType.Void
            Type.BOOLEAN -> ShaderBytecodeType.Bool

            Type.BYTE -> ShaderBytecodeType.Integer.BYTE
            Type.SHORT -> ShaderBytecodeType.Integer.SHORT
            Type.INT -> ShaderBytecodeType.Integer.JAVA
            Type.LONG -> ShaderBytecodeType.Integer.LONG

            Type.FLOAT -> ShaderBytecodeType.Float.JAVA
            Type.DOUBLE -> ShaderBytecodeType.Float.DOUBLE

            Type.METHOD -> ShaderBytecodeType.Function(convertType(type.returnType), type.argumentTypes.map { convertType(it) })
            // TODO other types

            else -> throw IllegalArgumentException("Cannot convert type $type to spir-v")
        }
    }

    @JvmStatic
    fun compileField(node: MethodNode): ShaderVariable? {
        var storageClass: Int? = null
        var name = node.name
        var type: Type? = null
        var location: Int? = null

        node.visibleAnnotations?.forEach { anno ->
            when (anno.desc) {
                $$"Lnet/typho/big_shot/loader/shaders/reflect/JavaShader$Input;" -> {
                    storageClass = STORAGE_CLASS_INPUT
                    type = Type.getReturnType(node.desc)
                    anno.forEach { key, value -> if (key == "name" && (value as String).isNotEmpty()) name = value }
                }
                $$"Lnet/typho/big_shot/loader/shaders/reflect/JavaShader$Output;" -> {
                    storageClass = STORAGE_CLASS_OUTPUT
                    type = Type.getArgumentTypes(node.desc)[0]
                    anno.forEach { key, value -> if (key == "name" && (value as String).isNotEmpty()) name = value }
                }
                $$"Lnet/typho/big_shot/loader/shaders/reflect/JavaShader$Uniform;" -> {
                    storageClass = STORAGE_CLASS_UNIFORM
                    type = Type.getReturnType(node.desc)
                    anno.forEach { key, value -> if (key == "name" && (value as String).isNotEmpty()) name = value }
                }
                $$"Lnet/typho/big_shot/loader/shaders/reflect/JavaShader$Location;" -> {
                    anno.forEach { key, value -> if (key == "value") location = value as Int }
                }
            }
        }

        storageClass ?: return null
        type ?: return null

        return ShaderVariable(ShaderBytecodeType.Pointer(storageClass, convertType(type)), label = ShaderLabelNode(name), location = location)
    }

    @JvmStatic
    fun compile(node: MethodNode): ShaderFunction {
        val func = ShaderFunction(convertType(Type.getMethodType(node.desc)) as ShaderBytecodeType.Function, label = ShaderLabelNode(node.name))

        func.instructions.apply {
            add(ShaderInsnNode(OP_LABEL, ShaderLabelNode()))
            // TODO
            add(ShaderInsnNode(OP_RETURN))
        }

        return func
    }

    @JvmStatic
    fun compile(node: ClassNode): ByteBuffer {
        val type = when (node.superName) {
            $$"net/typho/big_shot/loader/shaders/reflect/JavaShader$Vertex" -> EXEC_MODEL_VERTEX
            $$"net/typho/big_shot/loader/shaders/reflect/JavaShader$Fragment" -> EXEC_MODEL_FRAGMENT
            $$"net/typho/big_shot/loader/shaders/reflect/JavaShader$Geometry" -> EXEC_MODEL_GEOMETRY
            $$"net/typho/big_shot/loader/shaders/reflect/JavaShader$Compute" -> EXEC_MODEL_GL_COMPUTE
            else -> throw IllegalStateException("${node.name} does not directly extend any JavaShader type")
        }

        val builder = ShaderBytecodeBuilder(type)

        builder.capabilities.add(CAP_SHADER)
        builder.import("GLSL.std.450") // TODO

        node.methods.mapNotNullTo(builder.variables) { compileField(it) }

        val main = compile(MethodPointer.method().name("main").desc("()V").findOrThrow(node))
        builder.functions.add(main)

        return builder.build(main)
    }
}