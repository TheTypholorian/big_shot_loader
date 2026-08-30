package net.typho.big_shot.loader.shaders.reflect

import net.typho.asm_util.ASMUtil.forEach
import net.typho.asm_util.KotlinUtil.kotlinMetadata
import net.typho.asm_util.method.MethodPointer
import net.typho.big_shot.loader.shaders.bytecode.*
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.signature.SignatureReader
import org.objectweb.asm.signature.SignatureVisitor
import org.objectweb.asm.tree.*
import java.nio.ByteBuffer
import kotlin.metadata.jvm.KotlinClassMetadata
import kotlin.metadata.jvm.getterSignature
import kotlin.metadata.jvm.setterSignature

object JavaShaderCompiler {
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

    sealed interface StackValue {
        interface Labeled : StackValue {
            val label: ShaderLabelNode
        }

        data class Label(
            override val label: ShaderLabelNode
        ) : Labeled

        data class Constant(
            @JvmField
            val builder: ShaderBytecodeBuilder,
            @JvmField
            val const: ShaderConstant
        ) : Labeled {
            override val label: ShaderLabelNode by lazy { builder.getConstant(const) }
        }

        data class Array(
            @JvmField
            val variable: ShaderVariable
        ) : StackValue

        object This : StackValue
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

        return ShaderVariable(ShaderBytecodeType.Pointer(storageClass, ShaderBytecodeType.convertJavaType(type)), label = ShaderLabelNode(name), location = location)
    }

    @JvmStatic
    fun compile(node: MethodNode, variables: Map<MethodName, ShaderVariable>, builder: ShaderBytecodeBuilder): ShaderFunction {
        val func = ShaderFunction(ShaderBytecodeType.convertJavaType(Type.getMethodType(node.desc)) as ShaderBytecodeType.Function, label = ShaderLabelNode(node.name))

        func.instructions.apply {
            add(ShaderInsnNode(OP_LABEL, ShaderLabelNode()))

            val stack = mutableListOf<StackValue>()
            val locals = mutableMapOf<Int, ShaderVariable>()

            node.localVariables?.forEach { local ->
                if (node.access and Opcodes.ACC_STATIC != 0 || local.index != 0) { // "this" should be null in the stack
                    val type = ShaderBytecodeType.convertJavaType(Type.getType(local.signature ?: local.desc))

                    if (type !is ShaderBytecodeType.Array) { // arrays are defined later
                        val variable = ShaderVariable(ShaderBytecodeType.Pointer(STORAGE_CLASS_FUNCTION, type), ShaderLabelNode(local.name))
                        locals[local.index] = variable
                        add(ShaderInsnNode(OP_VARIABLE, variable.type, variable.label, variable.type.storageClass, variable.initializer))
                    }
                }
            }

            fun const(type: ShaderBytecodeType, value: Any) {
                stack.add(StackValue.Constant(builder, ShaderConstant(type, value)))
            }

            fun cast(opcode: Int, to: ShaderBytecodeType) {
                val v = stack.removeLast() as StackValue.Labeled

                if (v is StackValue.Constant) {
                    v.const.cast(to)?.let {
                        stack.add(StackValue.Constant(v.builder, it))
                        return
                    }
                }

                val r = ShaderLabelNode()
                add(ShaderInsnNode(opcode, to, r, v.label))
                stack.add(StackValue.Label(r))
            }

            fun math(opcode: Int, result: ShaderBytecodeType) {
                val b = (stack.removeLast() as StackValue.Labeled).label
                val a = (stack.removeLast() as StackValue.Labeled).label
                val r = ShaderLabelNode()
                add(ShaderInsnNode(opcode, result, r, a, b))
                stack.add(StackValue.Label(r))
            }

            fun mathUnary(opcode: Int, result: ShaderBytecodeType) {
                val a = (stack.removeLast() as StackValue.Labeled).label
                val r = ShaderLabelNode()
                add(ShaderInsnNode(opcode, result, r, a))
                stack.add(StackValue.Label(r))
            }

            for (insn in node.instructions) {
                when (insn) {
                    is LabelNode, is LineNumberNode -> continue
                    is VarInsnNode -> {
                        when (insn.opcode) {
                            Opcodes.ILOAD, Opcodes.LLOAD, Opcodes.FLOAD, Opcodes.DLOAD, Opcodes.ALOAD -> {
                                val local = locals[insn.`var`]

                                if (local == null) {
                                    stack.add(StackValue.This)
                                } else if (local.type.type is ShaderBytecodeType.Array) {
                                    stack.add(StackValue.Array(local))
                                } else {
                                    val label = ShaderLabelNode()
                                    stack.add(StackValue.Label(label))
                                    add(ShaderInsnNode(OP_LOAD, local.type.type, label, local.label))
                                }
                            }
                            Opcodes.ISTORE, Opcodes.LSTORE, Opcodes.FSTORE, Opcodes.DSTORE, Opcodes.ASTORE -> {
                                val value = stack.removeLast()

                                if (value is StackValue.Array) {
                                    if (locals[insn.`var`] != null) {
                                        TODO("reassigning arrays?")
                                    }

                                    locals[insn.`var`] = value.variable

                                    if (value.variable.label.name == null) {
                                        value.variable.label.name = node.localVariables?.firstOrNull { it.index == insn.`var` }?.name
                                    }
                                } else {
                                    val local = locals[insn.`var`]!!

                                    add(ShaderInsnNode(OP_STORE, local.label, (value as StackValue.Labeled).label))
                                }
                            }
                            else -> TODO()
                        }

                        continue
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

                                            if (target != StackValue.This) {
                                                TODO()
                                            }

                                            val result = ShaderLabelNode()
                                            add(ShaderInsnNode(OP_LOAD, v.type.type, result, v.label))
                                            stack.add(StackValue.Label(result))
                                            continue
                                        }
                                        STORAGE_CLASS_OUTPUT -> {
                                            val value = stack.removeLast()
                                            val target = stack.removeLast()

                                            if (target != StackValue.This || value == StackValue.This) {
                                                TODO()
                                            }

                                            add(ShaderInsnNode(OP_STORE, v.label, (value as StackValue.Labeled).label))
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
                    is IntInsnNode -> {
                        when (insn.opcode) {
                            Opcodes.BIPUSH, Opcodes.SIPUSH -> const(ShaderBytecodeType.Integer.JAVA, insn.operand)
                            Opcodes.NEWARRAY -> {
                                val length = stack.removeLast()

                                if (length !is StackValue.Constant) {
                                    throw IllegalStateException("Cannot create arrays of dynamic size")
                                }

                                val type = when (insn.operand) {
                                    Opcodes.T_BOOLEAN -> ShaderBytecodeType.Bool
                                    Opcodes.T_BYTE -> ShaderBytecodeType.Integer.BYTE
                                    Opcodes.T_CHAR, Opcodes.T_SHORT -> ShaderBytecodeType.Integer.SHORT
                                    Opcodes.T_INT -> ShaderBytecodeType.Integer.JAVA
                                    Opcodes.T_LONG -> ShaderBytecodeType.Integer.LONG
                                    Opcodes.T_FLOAT -> ShaderBytecodeType.Float.JAVA
                                    Opcodes.T_DOUBLE -> ShaderBytecodeType.Float.DOUBLE
                                    else -> throw AssertionError()
                                }
                                val variable = ShaderVariable(ShaderBytecodeType.Pointer(STORAGE_CLASS_FUNCTION, ShaderBytecodeType.Array(type, length.const.value.first() as Int)))
                                stack.add(StackValue.Array(variable))
                                add(ShaderInsnNode(OP_VARIABLE, variable.type, variable.label, variable.type.storageClass, variable.initializer))
                            }
                        }
                        continue
                    }
                    else -> {
                        when (insn.opcode) {
                            Opcodes.NOP -> add(ShaderInsnNode(OP_NO_OP))

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

                            Opcodes.IALOAD, Opcodes.LALOAD, Opcodes.FALOAD, Opcodes.DALOAD, Opcodes.AALOAD, Opcodes.BALOAD, Opcodes.CALOAD, Opcodes.SALOAD -> {
                                val index = (stack.removeLast() as StackValue.Labeled).label
                                val array = stack.removeLast() as StackValue.Array

                                val pointer = ShaderLabelNode()
                                val value = ShaderLabelNode()
                                add(ShaderInsnNode(OP_ACCESS_CHAIN, array.variable.type, pointer, array.variable.label, index))
                                add(ShaderInsnNode(OP_LOAD, array.variable.type.type.rootType, value, pointer))
                                stack.add(StackValue.Label(value))
                            }
                            Opcodes.IASTORE, Opcodes.LASTORE, Opcodes.FASTORE, Opcodes.DASTORE, Opcodes.AASTORE, Opcodes.BASTORE, Opcodes.CASTORE, Opcodes.SASTORE -> {
                                val value = (stack.removeLast() as StackValue.Labeled).label
                                val index = (stack.removeLast() as StackValue.Labeled).label
                                val array = stack.removeLast() as StackValue.Array

                                val pointer = ShaderLabelNode()
                                add(ShaderInsnNode(OP_ACCESS_CHAIN, array.variable.type, pointer, array.variable.label, index))
                                add(ShaderInsnNode(OP_STORE, pointer, value))
                            }

                            Opcodes.POP -> stack.removeLast()
                            Opcodes.POP2 -> {
                                stack.removeLast()
                                stack.removeLast()
                            }
                            Opcodes.DUP -> stack.addLast(stack.last())
                            Opcodes.DUP_X1, Opcodes.DUP_X2, Opcodes.DUP2, Opcodes.DUP2_X1, Opcodes.DUP2_X2 -> TODO("DUP opcode ${insn.opcode}")

                            Opcodes.I2L -> cast(OP_S_CONVERT, ShaderBytecodeType.Integer.LONG)
                            Opcodes.I2F -> cast(OP_CONVERT_S_TO_F, ShaderBytecodeType.Float.JAVA)
                            Opcodes.I2D -> cast(OP_CONVERT_S_TO_F, ShaderBytecodeType.Float.DOUBLE)
                            Opcodes.L2I -> cast(OP_S_CONVERT, ShaderBytecodeType.Integer.JAVA)
                            Opcodes.L2F -> cast(OP_CONVERT_S_TO_F, ShaderBytecodeType.Float.JAVA)
                            Opcodes.L2D -> cast(OP_CONVERT_S_TO_F, ShaderBytecodeType.Float.DOUBLE)
                            Opcodes.F2I -> cast(OP_CONVERT_F_TO_S, ShaderBytecodeType.Integer.JAVA)
                            Opcodes.F2L -> cast(OP_CONVERT_F_TO_S, ShaderBytecodeType.Integer.LONG)
                            Opcodes.F2D -> cast(OP_F_CONVERT, ShaderBytecodeType.Float.DOUBLE)
                            Opcodes.D2I -> cast(OP_CONVERT_F_TO_S, ShaderBytecodeType.Integer.JAVA)
                            Opcodes.D2L -> cast(OP_CONVERT_F_TO_S, ShaderBytecodeType.Integer.LONG)
                            Opcodes.D2F -> cast(OP_F_CONVERT, ShaderBytecodeType.Float.JAVA)
                            Opcodes.I2B -> cast(OP_S_CONVERT, ShaderBytecodeType.Integer.BYTE)
                            Opcodes.I2C, Opcodes.I2S -> cast(OP_S_CONVERT, ShaderBytecodeType.Integer.SHORT)

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