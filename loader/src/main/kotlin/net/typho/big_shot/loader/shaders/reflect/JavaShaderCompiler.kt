package net.typho.big_shot.loader.shaders.reflect

import net.typho.asm_util.ASMUtil.forEach
import net.typho.asm_util.KotlinUtil.kotlinMetadata
import net.typho.asm_util.method.MethodPointer
import net.typho.big_shot.loader.shaders.bytecode.*
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
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

    class Stack {
        private val contents = mutableListOf<StackValue>()

        fun push(value: StackValue) {
            contents.add(value)
            println("${contents.size} pushed $value")
        }

        fun pop() = contents.removeLast().also { println("${contents.size} popped $it") }

        fun popVectorComponents(type: ShaderBytecodeType.Vector): Array<StackValue.Labeled> = Array(type.componentCount) { pop() as StackValue.Labeled }.reversedArray()

        fun popSingleVectorComponent(type: ShaderBytecodeType.Vector): Array<StackValue.Labeled> {
            val v = pop() as StackValue.Labeled
            return Array(type.componentCount) { v }
        }

        fun dup() {
            contents.add(contents.last().also { println("${contents.size + 1} dup $it") })
        }

        fun peek() = contents.lastOrNull()

        fun replace(value: StackValue, with: StackValue) {
            contents.replaceAll { if (it == value) with else it }
            println("${contents.size} replaced $value with $with")
        }

        fun isEmpty() = contents.isEmpty()
    }

    sealed interface StackValue {
        interface Labeled : StackValue {
            val label: ShaderLabelNode
        }

        data class Label(
            override val label: ShaderLabelNode
        ) : Labeled

        data class LoadVariable(
            private val label0: ShaderLabelNode,
            @JvmField
            val variable: ShaderVariable,
            @JvmField
            val load: Runnable
        ) : Labeled {
            override val label: ShaderLabelNode by lazy {
                load.run()
                label0
            }
        }

        data class Constant(
            @JvmField
            val builder: ShaderBytecodeBuilder,
            @JvmField
            val const: ShaderConstant
        ) : Labeled {
            override val label: ShaderLabelNode by lazy { builder.getConstant(const) }
        }

        data class StringConstant(
            @JvmField
            val const: String
        ) : StackValue

        data class Array(
            @JvmField
            val variable: ShaderVariable
        ) : StackValue

        data class NewObject(
            @JvmField
            val index: Int
        ) : StackValue {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other !is NewObject) return false

                if (index != other.index) return false

                return true
            }

            override fun hashCode(): Int {
                return index
            }
        }

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

            val stack = Stack()
            val locals = mutableMapOf<Int, ShaderVariable>()
            var idCounter = 0

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
                stack.push(StackValue.Constant(builder, ShaderConstant(type, value)))
            }

            fun cast(opcode: Int, to: ShaderBytecodeType) {
                val v = stack.pop() as StackValue.Labeled

                if (v is StackValue.Constant) {
                    v.const.tryCast(to)?.let {
                        stack.push(StackValue.Constant(v.builder, it))
                        return
                    }
                }

                val r = ShaderLabelNode()
                add(ShaderInsnNode(opcode, to, r, v.label))
                stack.push(StackValue.Label(r))
            }

            fun math(opcode: Int, result: ShaderBytecodeType) {
                val b = (stack.pop() as StackValue.Labeled).label
                val a = (stack.pop() as StackValue.Labeled).label
                val r = ShaderLabelNode()
                add(ShaderInsnNode(opcode, result, r, a, b))
                stack.push(StackValue.Label(r))
            }

            fun mathUnary(opcode: Int, result: ShaderBytecodeType) {
                val a = (stack.pop() as StackValue.Labeled).label
                val r = ShaderLabelNode()
                add(ShaderInsnNode(opcode, result, r, a))
                stack.push(StackValue.Label(r))
            }

            fun createVector(type: ShaderBytecodeType.Vector, vararg values: StackValue.Labeled): ShaderLabelNode {
                return if (values.all { it is StackValue.Constant }) {
                    builder.getConstant(ShaderConstant(type, values.map { it.label }))
                } else {
                    val result = ShaderLabelNode()
                    add(ShaderInsnNode(OP_COMPOSITE_CONSTRUCT, type, result, values.map { it.label }))
                    result
                }
            }

            fun vectorStoreLoad(result: ShaderLabelNode, dest: StackValue) {
                if (dest is StackValue.LoadVariable) {
                    add(ShaderInsnNode(OP_STORE, dest.variable.label, result))
                    val result1 = ShaderLabelNode()
                    stack.push(StackValue.LoadVariable(result1, dest.variable) {
                        add(ShaderInsnNode(OP_LOAD, dest.variable.type.type, result1, dest.variable.label))
                    })
                } else {
                    stack.push(StackValue.Label(result))
                }
            }

            fun vectorOp(opcode: Int, type: ShaderBytecodeType.Vector, dest: StackValue, add: ShaderLabelNode, self: ShaderLabelNode) {
                val result = ShaderLabelNode()
                add(ShaderInsnNode(
                    opcode,
                    type,
                    result,
                    self,
                    add
                ))
                vectorStoreLoad(result, dest)
            }

            fun vectorOpSelf(opcode: Int, type: ShaderBytecodeType.Vector, add: ShaderLabelNode, self: StackValue.Labeled) {
                vectorOp(opcode, type, self, add, self.label)
            }

            for (insn in node.instructions) {
                when (insn) {
                    is LabelNode, is LineNumberNode -> continue
                    is VarInsnNode -> {
                        when (insn.opcode) {
                            Opcodes.ILOAD, Opcodes.LLOAD, Opcodes.FLOAD, Opcodes.DLOAD, Opcodes.ALOAD -> {
                                val local = locals[insn.`var`]

                                if (local == null) {
                                    stack.push(StackValue.This)
                                } else if (local.type.type is ShaderBytecodeType.Array) {
                                    stack.push(StackValue.Array(local))
                                } else {
                                    val label = ShaderLabelNode()
                                    stack.push(StackValue.LoadVariable(label, local) {
                                        add(ShaderInsnNode(OP_LOAD, local.type.type, label, local.label))
                                    })
                                }
                            }
                            Opcodes.ISTORE, Opcodes.LSTORE, Opcodes.FSTORE, Opcodes.DSTORE, Opcodes.ASTORE -> {
                                val value = stack.pop()

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
                        fun vector(type: ShaderBytecodeType.Vector, prim: String, name: String) {
                            val fullPrim = prim.repeat(type.componentCount)

                            fun op(opcode: Int) {
                                when (insn.desc) {
                                    "(${prim}Lorg/joml/$name;)Lorg/joml/$name;" -> vectorOp(opcode, type, stack.pop(), createVector(type, *stack.popSingleVectorComponent(type)), (stack.pop() as StackValue.Labeled).label)
                                    "(${fullPrim}Lorg/joml/$name;)Lorg/joml/$name;" -> vectorOp(opcode, type, stack.pop(), createVector(type, *stack.popVectorComponents(type)), (stack.pop() as StackValue.Labeled).label)
                                    "(Lorg/joml/${name}c;Lorg/joml/$name;)Lorg/joml/$name;" -> vectorOp(opcode, type, stack.pop(), (stack.pop() as StackValue.Labeled).label, (stack.pop() as StackValue.Labeled).label)

                                    "(${prim})Lorg/joml/$name;" -> vectorOpSelf(opcode, type, createVector(type, *stack.popSingleVectorComponent(type)), stack.pop() as StackValue.Labeled)
                                    "(${fullPrim})Lorg/joml/$name;" -> vectorOpSelf(opcode, type, createVector(type, *stack.popVectorComponents(type)), stack.pop() as StackValue.Labeled)
                                    "(Lorg/joml/${name}c;)Lorg/joml/$name;" -> vectorOpSelf(opcode, type, (stack.pop() as StackValue.Labeled).label, stack.pop() as StackValue.Labeled)

                                    else -> TODO()
                                }
                            }

                            when (insn.name) {
                                "add" -> op(if (type.componentType is ShaderBytecodeType.Integer) OP_I_ADD else OP_F_ADD)
                                "sub" -> op(if (type.componentType is ShaderBytecodeType.Integer) OP_I_SUB else OP_F_SUB)
                                "mul" -> op(if (type.componentType is ShaderBytecodeType.Integer) OP_I_MUL else OP_F_MUL)
                                "div" -> op(if (type.componentType is ShaderBytecodeType.Integer) OP_S_DIV else OP_F_DIV)
                                "length" -> TODO()
                                "<init>" -> {
                                    when (insn.desc) {
                                        "()V" -> {
                                            val vec = createVector(type, StackValue.Label(builder.getConstant(ShaderConstant(ShaderBytecodeType.INT, 0).tryCast(type.componentType)!!)))
                                            val self = stack.pop() as StackValue.NewObject
                                            stack.replace(self, StackValue.Label(vec))
                                        }
                                        "(${prim})V" -> {
                                            val vec = createVector(type, *stack.popSingleVectorComponent(type))
                                            val self = stack.pop() as StackValue.NewObject
                                            stack.replace(self, StackValue.Label(vec))
                                        }
                                        "(${fullPrim})V" -> {
                                            val vec = createVector(type, *stack.popVectorComponents(type))
                                            val self = stack.pop() as StackValue.NewObject
                                            stack.replace(self, StackValue.Label(vec))
                                        }
                                        "(Lorg/joml/${name}c;)V" -> {
                                            val vec = createVector(type, stack.pop() as StackValue.Labeled)
                                            val self = stack.pop() as StackValue.NewObject
                                            stack.replace(self, StackValue.Label(vec))
                                        }
                                        else -> TODO()
                                    }
                                }
                                else -> TODO()
                            }
                        }

                        when (insn.owner) {
                            "org/joml/Vector2f", "org/joml/Vector2fc" -> {
                                vector(ShaderBytecodeType.VECTOR2F, "F", "Vector2f")
                                continue
                            }
                            "org/joml/Vector3f", "org/joml/Vector3fc" -> {
                                vector(ShaderBytecodeType.VECTOR3F, "F", "Vector3f")
                                continue
                            }
                            "org/joml/Vector4f", "org/joml/Vector4fc" -> {
                                vector(ShaderBytecodeType.VECTOR4F, "F", "Vector4f")
                                continue
                            }
                            "kotlin/jvm/internal/Intrinsics" -> {
                                repeat(Type.getArgumentCount(insn.desc)) {
                                    stack.pop()
                                }

                                if (insn.opcode != Opcodes.INVOKESTATIC) {
                                    stack.pop()
                                }

                                continue
                            }
                        }

                        val name = MethodName(insn.owner, insn.name, insn.desc)

                        variables[name]?.let { v ->
                            when (v.type.storageClass) {
                                STORAGE_CLASS_INPUT -> {
                                    val target = stack.pop()

                                    if (target != StackValue.This) {
                                        TODO()
                                    }

                                    val result = ShaderLabelNode()
                                    stack.push(StackValue.LoadVariable(result, v) {
                                        add(ShaderInsnNode(OP_LOAD, v.type.type, result, v.label))
                                    })
                                    continue
                                }
                                STORAGE_CLASS_OUTPUT -> {
                                    val value = stack.pop()
                                    val target = stack.pop()

                                    if (target != StackValue.This || value == StackValue.This) {
                                        TODO()
                                    }

                                    add(ShaderInsnNode(OP_STORE, v.label, (value as StackValue.Labeled).label))
                                    continue
                                }
                                else -> TODO()
                            }
                        }

                        TODO("call method $name")
                    }
                    is LdcInsnNode -> {
                        when (val const = insn.cst) {
                            is Boolean -> const(ShaderBytecodeType.Bool, const)
                            is Byte -> const(ShaderBytecodeType.BYTE, const)
                            is Short -> const(ShaderBytecodeType.SHORT, const)
                            is Int -> const(ShaderBytecodeType.INT, const)
                            is Long -> const(ShaderBytecodeType.LONG, const)
                            is Float -> const(ShaderBytecodeType.FLOAT, const)
                            is Double -> const(ShaderBytecodeType.DOUBLE, const)
                            is String -> stack.push(StackValue.StringConstant(const))
                            else -> TODO("Unsupported constant $const")
                        }
                        continue
                    }
                    is IntInsnNode -> {
                        when (insn.opcode) {
                            Opcodes.BIPUSH, Opcodes.SIPUSH -> const(ShaderBytecodeType.INT, insn.operand)
                            Opcodes.NEWARRAY -> {
                                val length = stack.pop()

                                if (length !is StackValue.Constant) {
                                    throw IllegalStateException("Cannot create arrays of dynamic size")
                                }

                                val type = when (insn.operand) {
                                    Opcodes.T_BOOLEAN -> ShaderBytecodeType.Bool
                                    Opcodes.T_BYTE -> ShaderBytecodeType.BYTE
                                    Opcodes.T_CHAR, Opcodes.T_SHORT -> ShaderBytecodeType.SHORT
                                    Opcodes.T_INT -> ShaderBytecodeType.INT
                                    Opcodes.T_LONG -> ShaderBytecodeType.LONG
                                    Opcodes.T_FLOAT -> ShaderBytecodeType.FLOAT
                                    Opcodes.T_DOUBLE -> ShaderBytecodeType.DOUBLE
                                    else -> throw AssertionError()
                                }
                                val variable = ShaderVariable(ShaderBytecodeType.Pointer(STORAGE_CLASS_FUNCTION, ShaderBytecodeType.Array(type, length.const.value.first() as Int)))
                                stack.push(StackValue.Array(variable))
                                add(ShaderInsnNode(OP_VARIABLE, variable.type, variable.label, variable.type.storageClass, variable.initializer))
                            }
                        }
                        continue
                    }
                    is TypeInsnNode -> {
                        when (insn.opcode) {
                            Opcodes.NEW -> stack.push(StackValue.NewObject(idCounter++))
                            Opcodes.CHECKCAST -> {}
                            else -> TODO("${insn.opcode}")
                        }
                        continue
                    }
                    else -> {
                        when (insn.opcode) {
                            Opcodes.NOP -> add(ShaderInsnNode(OP_NO_OP))

                            Opcodes.ICONST_0 -> const(ShaderBytecodeType.INT, 0)
                            Opcodes.ICONST_1 -> const(ShaderBytecodeType.INT, 1)
                            Opcodes.ICONST_2 -> const(ShaderBytecodeType.INT, 2)
                            Opcodes.ICONST_3 -> const(ShaderBytecodeType.INT, 3)
                            Opcodes.ICONST_4 -> const(ShaderBytecodeType.INT, 4)
                            Opcodes.ICONST_5 -> const(ShaderBytecodeType.INT, 5)

                            Opcodes.LCONST_0 -> const(ShaderBytecodeType.LONG, 0L)
                            Opcodes.LCONST_1 -> const(ShaderBytecodeType.LONG, 1L)

                            Opcodes.FCONST_0 -> const(ShaderBytecodeType.FLOAT, 0f)
                            Opcodes.FCONST_1 -> const(ShaderBytecodeType.FLOAT, 1f)
                            Opcodes.FCONST_2 -> const(ShaderBytecodeType.FLOAT, 2f)

                            Opcodes.DCONST_0 -> const(ShaderBytecodeType.DOUBLE, 0.0)
                            Opcodes.DCONST_1 -> const(ShaderBytecodeType.DOUBLE, 1.0)

                            Opcodes.IALOAD, Opcodes.LALOAD, Opcodes.FALOAD, Opcodes.DALOAD, Opcodes.AALOAD, Opcodes.BALOAD, Opcodes.CALOAD, Opcodes.SALOAD -> {
                                val index = (stack.pop() as StackValue.Labeled).label
                                val array = stack.pop() as StackValue.Array

                                val pointer = ShaderLabelNode()
                                val value = ShaderLabelNode()
                                add(ShaderInsnNode(OP_ACCESS_CHAIN, array.variable.type, pointer, array.variable.label, index))
                                add(ShaderInsnNode(OP_LOAD, array.variable.type.type.rootType, value, pointer))
                                stack.push(StackValue.Label(value))
                            }
                            Opcodes.IASTORE, Opcodes.LASTORE, Opcodes.FASTORE, Opcodes.DASTORE, Opcodes.AASTORE, Opcodes.BASTORE, Opcodes.CASTORE, Opcodes.SASTORE -> {
                                val value = (stack.pop() as StackValue.Labeled).label
                                val index = (stack.pop() as StackValue.Labeled).label
                                val array = stack.pop() as StackValue.Array

                                val pointer = ShaderLabelNode()
                                add(ShaderInsnNode(OP_ACCESS_CHAIN, array.variable.type, pointer, array.variable.label, index))
                                add(ShaderInsnNode(OP_STORE, pointer, value))
                            }

                            Opcodes.POP -> stack.pop()
                            Opcodes.POP2 -> {
                                stack.pop()
                                stack.pop()
                            }
                            Opcodes.DUP -> stack.dup()
                            Opcodes.DUP_X1, Opcodes.DUP_X2, Opcodes.DUP2, Opcodes.DUP2_X1, Opcodes.DUP2_X2 -> TODO("DUP opcode ${insn.opcode}")

                            Opcodes.I2L -> cast(OP_S_CONVERT, ShaderBytecodeType.LONG)
                            Opcodes.I2F -> cast(OP_CONVERT_S_TO_F, ShaderBytecodeType.FLOAT)
                            Opcodes.I2D -> cast(OP_CONVERT_S_TO_F, ShaderBytecodeType.DOUBLE)
                            Opcodes.L2I -> cast(OP_S_CONVERT, ShaderBytecodeType.INT)
                            Opcodes.L2F -> cast(OP_CONVERT_S_TO_F, ShaderBytecodeType.FLOAT)
                            Opcodes.L2D -> cast(OP_CONVERT_S_TO_F, ShaderBytecodeType.DOUBLE)
                            Opcodes.F2I -> cast(OP_CONVERT_F_TO_S, ShaderBytecodeType.INT)
                            Opcodes.F2L -> cast(OP_CONVERT_F_TO_S, ShaderBytecodeType.LONG)
                            Opcodes.F2D -> cast(OP_F_CONVERT, ShaderBytecodeType.DOUBLE)
                            Opcodes.D2I -> cast(OP_CONVERT_F_TO_S, ShaderBytecodeType.INT)
                            Opcodes.D2L -> cast(OP_CONVERT_F_TO_S, ShaderBytecodeType.LONG)
                            Opcodes.D2F -> cast(OP_F_CONVERT, ShaderBytecodeType.FLOAT)
                            Opcodes.I2B -> cast(OP_S_CONVERT, ShaderBytecodeType.BYTE)
                            Opcodes.I2C, Opcodes.I2S -> cast(OP_S_CONVERT, ShaderBytecodeType.SHORT)

                            Opcodes.IADD -> math(OP_I_ADD, ShaderBytecodeType.INT)
                            Opcodes.LADD -> math(OP_I_ADD, ShaderBytecodeType.LONG)
                            Opcodes.FADD -> math(OP_F_ADD, ShaderBytecodeType.FLOAT)
                            Opcodes.DADD -> math(OP_F_ADD, ShaderBytecodeType.DOUBLE)

                            Opcodes.ISUB -> math(OP_I_SUB, ShaderBytecodeType.INT)
                            Opcodes.LSUB -> math(OP_I_SUB, ShaderBytecodeType.LONG)
                            Opcodes.FSUB -> math(OP_F_SUB, ShaderBytecodeType.FLOAT)
                            Opcodes.DSUB -> math(OP_F_SUB, ShaderBytecodeType.DOUBLE)

                            Opcodes.IMUL -> math(OP_I_MUL, ShaderBytecodeType.INT)
                            Opcodes.LMUL -> math(OP_I_MUL, ShaderBytecodeType.LONG)
                            Opcodes.FMUL -> math(OP_F_MUL, ShaderBytecodeType.FLOAT)
                            Opcodes.DMUL -> math(OP_F_MUL, ShaderBytecodeType.DOUBLE)

                            Opcodes.IDIV -> math(OP_S_DIV, ShaderBytecodeType.INT)
                            Opcodes.LDIV -> math(OP_S_DIV, ShaderBytecodeType.LONG)
                            Opcodes.FDIV -> math(OP_F_DIV, ShaderBytecodeType.FLOAT)
                            Opcodes.DDIV -> math(OP_F_DIV, ShaderBytecodeType.DOUBLE)

                            Opcodes.IREM -> math(OP_S_REM, ShaderBytecodeType.INT)
                            Opcodes.LREM -> math(OP_S_REM, ShaderBytecodeType.LONG)
                            Opcodes.FREM -> math(OP_F_REM, ShaderBytecodeType.FLOAT)
                            Opcodes.DREM -> math(OP_F_REM, ShaderBytecodeType.DOUBLE)

                            Opcodes.INEG -> mathUnary(OP_S_NEGATE, ShaderBytecodeType.INT)
                            Opcodes.LNEG -> mathUnary(OP_S_NEGATE, ShaderBytecodeType.LONG)
                            Opcodes.FNEG -> mathUnary(OP_F_NEGATE, ShaderBytecodeType.FLOAT)
                            Opcodes.DNEG -> mathUnary(OP_F_NEGATE, ShaderBytecodeType.DOUBLE)

                            Opcodes.ISHL -> math(OP_SHIFT_LEFT_LOGICAL, ShaderBytecodeType.INT)
                            Opcodes.LSHL -> math(OP_SHIFT_LEFT_LOGICAL, ShaderBytecodeType.LONG)
                            Opcodes.ISHR -> math(OP_SHIFT_RIGHT_ARITHMETIC, ShaderBytecodeType.INT)
                            Opcodes.LSHR -> math(OP_SHIFT_RIGHT_ARITHMETIC, ShaderBytecodeType.LONG)
                            Opcodes.IUSHR -> math(OP_SHIFT_RIGHT_LOGICAL, ShaderBytecodeType.INT)
                            Opcodes.LUSHR -> math(OP_SHIFT_RIGHT_LOGICAL, ShaderBytecodeType.LONG)
                            Opcodes.IAND -> math(OP_BITWISE_AND, ShaderBytecodeType.INT)
                            Opcodes.LAND -> math(OP_BITWISE_AND, ShaderBytecodeType.LONG)
                            Opcodes.IOR -> math(OP_BITWISE_OR, ShaderBytecodeType.INT)
                            Opcodes.LOR -> math(OP_BITWISE_OR, ShaderBytecodeType.LONG)
                            Opcodes.IXOR -> math(OP_BITWISE_XOR, ShaderBytecodeType.INT)
                            Opcodes.LXOR -> math(OP_BITWISE_XOR, ShaderBytecodeType.LONG)
                            // TODO rest of math opcodes

                            Opcodes.RETURN -> add(ShaderInsnNode(OP_RETURN))
                            Opcodes.IRETURN, Opcodes.LRETURN, Opcodes.FRETURN, Opcodes.DRETURN, Opcodes.ARETURN -> add(ShaderInsnNode(OP_RETURN_VALUE, stack.pop()))

                            else -> TODO("${insn.opcode}")
                        }
                        continue
                    }
                }

                TODO("unsupported op $insn ${insn.opcode}")
            }

            if (!stack.isEmpty()) {
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