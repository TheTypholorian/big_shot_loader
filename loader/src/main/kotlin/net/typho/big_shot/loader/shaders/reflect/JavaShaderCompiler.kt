package net.typho.big_shot.loader.shaders.reflect

import net.typho.asm_util.ASMUtil.forEach
import net.typho.asm_util.KotlinUtil.kotlinMetadata
import net.typho.asm_util.method.MethodPointer
import net.typho.big_shot.loader.shaders.bytecode.*
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.LineNumberNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.VarInsnNode
import java.nio.ByteBuffer
import kotlin.metadata.jvm.KotlinClassMetadata
import kotlin.metadata.jvm.getterSignature
import kotlin.metadata.jvm.setterSignature

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

    data class MethodName(
        @JvmField
        val owner: String,
        @JvmField
        val name: String,
        @JvmField
        val desc: String
    ) {
        override fun toString(): String {
            return "$owner.$name$desc"
        }
    }

    @JvmStatic
    fun compileField(node: MethodNode, ktMeta: KotlinClassMetadata?): ShaderVariable? {
        var storageClass: Int? = null
        var name = node.name
        var type: Type? = null
        var location: Int? = null

        if (ktMeta is KotlinClassMetadata.Class) {
            for (property in ktMeta.kmClass.properties) {
                if (property.getterSignature?.let { it.name == name && it.descriptor == node.desc } == true) {
                    name = property.name
                    break
                } else if (property.setterSignature?.let { it.name == name && it.descriptor == node.desc } == true) {
                    name = property.name
                    break
                }
            }
        }

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
    fun compile(node: MethodNode, variables: Map<MethodName, ShaderVariable>, builder: ShaderBytecodeBuilder): ShaderFunction {
        val func = ShaderFunction(convertType(Type.getMethodType(node.desc)) as ShaderBytecodeType.Function, label = ShaderLabelNode(node.name))

        func.instructions.apply {
            add(ShaderInsnNode(OP_LABEL, ShaderLabelNode()))

            val stack = mutableListOf<ShaderLabelNode?>()

            fun const(type: ShaderBytecodeType, value: Any) {
                stack.add(builder.getConstant(ShaderConstant(type, value)))
            }

            fun cast(opcode: Int, result: ShaderBytecodeType) {
                val v = stack.removeLast()
                val r = ShaderLabelNode()
                add(ShaderInsnNode(opcode, result, r, v))
                stack.add(r)
            }

            fun math(opcode: Int, result: ShaderBytecodeType) {
                val b = stack.removeLast()
                val a = stack.removeLast()
                val r = ShaderLabelNode()
                add(ShaderInsnNode(opcode, result, r, a, b))
                stack.add(r)
            }

            fun mathUnary(opcode: Int, result: ShaderBytecodeType) {
                val a = stack.removeLast()
                val r = ShaderLabelNode()
                add(ShaderInsnNode(opcode, result, r, a))
                stack.add(r)
            }

            for (insn in node.instructions) {
                when (insn) {
                    is LabelNode, is LineNumberNode -> continue
                    is VarInsnNode -> {
                        when (insn.opcode) {
                            Opcodes.ALOAD -> {
                                if (insn.`var` != 0) {
                                    TODO("non-0 aload")
                                }

                                stack.add(null)
                                continue
                            }
                        }
                    }
                    is MethodInsnNode -> {
                        when (insn.opcode) {
                            Opcodes.INVOKESTATIC -> {}
                            Opcodes.INVOKEVIRTUAL, Opcodes.INVOKESPECIAL, Opcodes.INVOKEINTERFACE -> {
                                val name = MethodName(insn.owner, insn.name, insn.desc)

                                variables[name]?.let { v ->
                                    when (v.type.storageClass) {
                                        STORAGE_CLASS_INPUT -> {
                                            val target = stack.removeLast()

                                            if (target != null) {
                                                TODO()
                                            }

                                            val result = ShaderLabelNode()
                                            add(ShaderInsnNode(OP_LOAD, v.type.type, result, v.label))
                                            stack.add(result)
                                            continue
                                        }
                                        STORAGE_CLASS_OUTPUT -> {
                                            val value = stack.removeLast()
                                            val target = stack.removeLast()

                                            if (target != null || value == null) {
                                                TODO()
                                            }

                                            add(ShaderInsnNode(OP_STORE, v.label, value))
                                            continue
                                        }
                                    }
                                }

                                TODO("call method $name")
                            }
                            Opcodes.INVOKEDYNAMIC -> {}
                        }
                    }
                    is LdcInsnNode -> {
                        when (insn.cst) {
                            is Boolean -> const(ShaderBytecodeType.Bool, insn.cst)
                            is Byte -> const(ShaderBytecodeType.Integer.BYTE, insn.cst)
                            is Short -> const(ShaderBytecodeType.Integer.SHORT, insn.cst)
                            is Int -> const(ShaderBytecodeType.Integer.JAVA, insn.cst)
                            is Long -> const(ShaderBytecodeType.Integer.LONG, insn.cst)
                            is Float -> const(ShaderBytecodeType.Float.JAVA, insn.cst)
                            is Double -> const(ShaderBytecodeType.Float.DOUBLE, insn.cst)
                            else -> TODO("Unsupported constant ${insn.cst}")
                        }
                        continue
                    }
                    else -> {
                        when (insn.opcode) {
                            Opcodes.ICONST_0 -> const(ShaderBytecodeType.Integer.JAVA, 0)
                            Opcodes.ICONST_1 -> const(ShaderBytecodeType.Integer.JAVA, 1)
                            Opcodes.ICONST_2 -> const(ShaderBytecodeType.Integer.JAVA, 2)
                            Opcodes.ICONST_3 -> const(ShaderBytecodeType.Integer.JAVA, 3)
                            Opcodes.ICONST_4 -> const(ShaderBytecodeType.Integer.JAVA, 4)
                            Opcodes.ICONST_5 -> const(ShaderBytecodeType.Integer.JAVA, 5)

                            Opcodes.LCONST_0 -> const(ShaderBytecodeType.Integer.LONG, 0L)
                            Opcodes.LCONST_1 -> const(ShaderBytecodeType.Integer.LONG, 1L)

                            Opcodes.FCONST_0 -> const(ShaderBytecodeType.Float.JAVA, 0f)
                            Opcodes.FCONST_1 -> const(ShaderBytecodeType.Float.JAVA, 1f)
                            Opcodes.FCONST_2 -> const(ShaderBytecodeType.Float.JAVA, 2f)

                            Opcodes.DCONST_0 -> const(ShaderBytecodeType.Float.DOUBLE, 0.0)
                            Opcodes.DCONST_1 -> const(ShaderBytecodeType.Float.DOUBLE, 1.0)

                            Opcodes.I2L -> cast(OP_S_CONVERT, ShaderBytecodeType.Integer.LONG)
                            Opcodes.I2F -> cast(OP_CONVERT_S_TO_F, ShaderBytecodeType.Float.JAVA)
                            Opcodes.I2D -> cast(OP_CONVERT_S_TO_F, ShaderBytecodeType.Float.DOUBLE)
                            // TODO rest of conversion opcodes

                            Opcodes.IADD -> math(OP_I_ADD, ShaderBytecodeType.Integer.JAVA)
                            Opcodes.LADD -> math(OP_I_ADD, ShaderBytecodeType.Integer.LONG)
                            Opcodes.FADD -> math(OP_F_ADD, ShaderBytecodeType.Float.JAVA)
                            Opcodes.DADD -> math(OP_F_ADD, ShaderBytecodeType.Float.DOUBLE)

                            Opcodes.ISUB -> math(OP_I_SUB, ShaderBytecodeType.Integer.JAVA)
                            Opcodes.LSUB -> math(OP_I_SUB, ShaderBytecodeType.Integer.LONG)
                            Opcodes.FSUB -> math(OP_F_SUB, ShaderBytecodeType.Float.JAVA)
                            Opcodes.DSUB -> math(OP_F_SUB, ShaderBytecodeType.Float.DOUBLE)

                            Opcodes.IMUL -> math(OP_I_MUL, ShaderBytecodeType.Integer.JAVA)
                            Opcodes.LMUL -> math(OP_I_MUL, ShaderBytecodeType.Integer.LONG)
                            Opcodes.FMUL -> math(OP_F_MUL, ShaderBytecodeType.Float.JAVA)
                            Opcodes.DMUL -> math(OP_F_MUL, ShaderBytecodeType.Float.DOUBLE)

                            Opcodes.IDIV -> math(OP_S_DIV, ShaderBytecodeType.Integer.JAVA)
                            Opcodes.LDIV -> math(OP_S_DIV, ShaderBytecodeType.Integer.LONG)
                            Opcodes.FDIV -> math(OP_F_DIV, ShaderBytecodeType.Float.JAVA)
                            Opcodes.DDIV -> math(OP_F_DIV, ShaderBytecodeType.Float.DOUBLE)

                            Opcodes.IREM -> math(OP_S_REM, ShaderBytecodeType.Integer.JAVA)
                            Opcodes.LREM -> math(OP_S_REM, ShaderBytecodeType.Integer.LONG)
                            Opcodes.FREM -> math(OP_F_REM, ShaderBytecodeType.Float.JAVA)
                            Opcodes.DREM -> math(OP_F_REM, ShaderBytecodeType.Float.DOUBLE)

                            Opcodes.INEG -> mathUnary(OP_S_NEGATE, ShaderBytecodeType.Integer.JAVA)
                            Opcodes.LNEG -> mathUnary(OP_S_NEGATE, ShaderBytecodeType.Integer.LONG)
                            Opcodes.FNEG -> mathUnary(OP_F_NEGATE, ShaderBytecodeType.Float.JAVA)
                            Opcodes.DNEG -> mathUnary(OP_F_NEGATE, ShaderBytecodeType.Float.DOUBLE)

                            Opcodes.ISHL -> math(OP_SHIFT_LEFT_LOGICAL, ShaderBytecodeType.Integer.JAVA)
                            Opcodes.LSHL -> math(OP_SHIFT_LEFT_LOGICAL, ShaderBytecodeType.Integer.LONG)
                            Opcodes.ISHR -> math(OP_SHIFT_RIGHT_ARITHMETIC, ShaderBytecodeType.Integer.JAVA)
                            Opcodes.LSHR -> math(OP_SHIFT_RIGHT_ARITHMETIC, ShaderBytecodeType.Integer.LONG)
                            Opcodes.IUSHR -> math(OP_SHIFT_RIGHT_LOGICAL, ShaderBytecodeType.Integer.JAVA)
                            Opcodes.LUSHR -> math(OP_SHIFT_RIGHT_LOGICAL, ShaderBytecodeType.Integer.LONG)
                            Opcodes.IAND -> math(OP_BITWISE_AND, ShaderBytecodeType.Integer.JAVA)
                            Opcodes.LAND -> math(OP_BITWISE_AND, ShaderBytecodeType.Integer.LONG)
                            Opcodes.IOR -> math(OP_BITWISE_OR, ShaderBytecodeType.Integer.JAVA)
                            Opcodes.LOR -> math(OP_BITWISE_OR, ShaderBytecodeType.Integer.LONG)
                            Opcodes.IXOR -> math(OP_BITWISE_XOR, ShaderBytecodeType.Integer.JAVA)
                            Opcodes.LXOR -> math(OP_BITWISE_XOR, ShaderBytecodeType.Integer.LONG)
                            // TODO rest of math opcodes

                            Opcodes.RETURN -> add(ShaderInsnNode(OP_RETURN))
                            Opcodes.IRETURN, Opcodes.LRETURN, Opcodes.FRETURN, Opcodes.DRETURN, Opcodes.ARETURN -> add(ShaderInsnNode(OP_RETURN_VALUE, stack.removeLast()))

                            else -> TODO("${insn.opcode}")
                        }
                        continue
                    }
                }

                TODO("unsupported op $insn ${insn.opcode}")
            }

            if (stack.isNotEmpty()) {
                throw IllegalStateException("Stack is not empty at the end of the method")
            }
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

        val ktMeta = node.visibleAnnotations?.firstOrNull { it.desc == "Lkotlin/Metadata;" }?.kotlinMetadata?.let { KotlinClassMetadata.readLenient(it) }

        val variables = mutableMapOf<MethodName, ShaderVariable>()
        node.methods.mapNotNullTo(builder.variables) {
            val field = compileField(it, ktMeta)

            if (field != null) {
                variables[MethodName(node.name, it.name, it.desc)] = field
            }

            field
        }

        val main = compile(MethodPointer.method().name("main").desc("()V").findOrThrow(node), variables, builder)
        builder.functions.add(main)

        return builder.build(main)
    }
}