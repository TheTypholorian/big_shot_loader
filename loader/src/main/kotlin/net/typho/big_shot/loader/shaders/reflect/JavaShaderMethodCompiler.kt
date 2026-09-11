package net.typho.big_shot.loader.shaders.reflect

import net.typho.asm_util.method.MethodPointer
import net.typho.big_shot.loader.shaders.bytecode.*
import net.typho.big_shot.loader.shaders.reflect.JomlVectorTypeHandler.Companion.createVector
import net.typho.big_shot.loader.shaders.reflect.JomlVectorTypeHandler.Companion.vectorStoreLoad
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.LineNumberNode
import org.objectweb.asm.tree.LocalVariableNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TypeInsnNode
import org.objectweb.asm.tree.VarInsnNode

class JavaShaderMethodCompiler(
    @JvmField
    val parent: JavaShaderCompiler,
    @JvmField
    val node: MethodNode,
    @JvmField
    val function: ShaderFunction
) {
    @JvmField
    val stack = Stack()
    @JvmField
    val locals = mutableMapOf<Int, Local>()
    private var remainingLocals = node.localVariables?.toMutableList() ?: mutableListOf()

    fun loadLocal(local: LocalVariableNode): Local {
        println("loading $local ${local.index}")
        remainingLocals.remove(local)

        return if (node.access and Opcodes.ACC_STATIC == 0 && local.index == 0) {
            Local.This(local)
        } else {
            val javaType = Type.getType(local.desc)
            val type = ShaderBytecodeType.convertJavaType(javaType)

            if (type is ShaderBytecodeType.Array) {
                Local.NewArray(type, local)
            } else {
                val variable = parent.getTypeHandler(javaType)?.createLocalVariable(this@JavaShaderMethodCompiler, local, javaType, type) ?: ShaderVariable(ShaderBytecodeType.Pointer(STORAGE_CLASS_FUNCTION, type), ShaderLabelNode(local.name), javaType = javaType)
                function.instructions.add(ShaderInsnNode(OP_VARIABLE, variable.type, variable.label, variable.type.storageClass, variable.initializer))
                Local.Variable(variable, local)
            }
        }
    }

    fun getOrLoadLocal(id: Int): Local? {
        locals[id]?.let { return it }

        val next = remainingLocals
            .filter { it.index == id }
            .minByOrNull { node.instructions.indexOf(it.start) }
            ?: return null
        val local = loadLocal(next)
        locals[id] = local
        return local
    }

    fun compile() {
        stack.clear()
        locals.clear()
        remainingLocals = node.localVariables?.toMutableList() ?: mutableListOf()

        if (node.access and Opcodes.ACC_STATIC == 0) {
            getOrLoadLocal(0)
        }

        function.instructions.clear()
        function.instructions.apply {
            repeat(Type.getArgumentCount(node.desc)) {
                val local = remainingLocals.removeFirst()
                val label = ShaderLabelNode(local.name)
                function.instructions.add(ShaderInsnNode(OP_FUNCTION_PARAMETER, ShaderBytecodeType.convertJavaType(Type.getType(local.desc)), label))
                locals[local.index] = Local.Argument(label, local)
            }

            function.instructions.add(ShaderInsnNode(OP_LABEL, ShaderLabelNode()))

            for (insn in node.instructions) {
                when (insn) {
                    is LabelNode -> {
                        locals.values.forEach {
                            if (it.local.end === insn) {
                                println("$it expired")
                            }
                        }
                        locals.values.removeIf { it.local.end === insn }

                        remainingLocals.filter { it.start === insn }.forEach { local ->
                            locals[local.index] = loadLocal(local)
                        }
                        continue
                    }
                    is LineNumberNode -> continue
                    is VarInsnNode -> {
                        when (insn.opcode) {
                            Opcodes.ILOAD, Opcodes.LLOAD, Opcodes.FLOAD, Opcodes.DLOAD, Opcodes.ALOAD -> {
                                val local = locals[insn.`var`]!!

                                if (local is Local.This) {
                                    stack.push(StackValue.This)
                                } else {
                                    stack.push(local.load(this@JavaShaderMethodCompiler)!!)
                                }
                            }
                            Opcodes.ISTORE, Opcodes.LSTORE, Opcodes.FSTORE, Opcodes.DSTORE, Opcodes.ASTORE -> {
                                val value = stack.pop()

                                if (value is StackValue.Array) {
                                    val local = getOrLoadLocal(insn.`var`)!!

                                    if (local !is Local.NewArray) {
                                        TODO("reassigning arrays?")
                                    }

                                    locals[insn.`var`] = Local.Variable(value.variable, local.local)

                                    if (value.variable.label.name == null) {
                                        value.variable.label.name = remainingLocals.firstOrNull { it.index == insn.`var` }?.name
                                    }
                                } else if (value is StackValue.LoadVariable && value.variable.type.type is ShaderBytecodeType.Vector) {
                                    throw JavaShaderCompilationException("Cannot store a mutable ${value.variable.type.type} value from one variable in another, since joml vectors are mutable while glsl vectors are immutable.")
                                } else {
                                    add(ShaderInsnNode(OP_STORE, getOrLoadLocal(insn.`var`)!!.variable!!.label, value.label!!))
                                }
                            }
                            else -> TODO()
                        }

                        continue
                    }
                    is MethodInsnNode -> {
                        fun vector(type: ShaderBytecodeType.Vector, prim: String, name: String) {
                            val fullPrim = prim.repeat(type.componentCount)

                            when (insn.name) {
                                "distance" -> when (insn.desc) {
                                    "(Lorg/joml/${name}c;)$prim" -> {
                                        val other = stack.pop()
                                        val self = stack.pop()
                                        val result = ShaderLabelNode()
                                        add(ShaderInsnNode(
                                            OP_EXT_INST,
                                            type.componentType,
                                            result,
                                            parent.builder.import("GLSL.std.450"),
                                            GLSL_DISTANCE,
                                            self.label,
                                            other.label
                                        ))
                                        stack.push(StackValue.Label(result))
                                    }
                                    else -> TODO()
                                }
                                "length" -> when (insn.desc) {
                                    "()$prim" -> {
                                        val self = stack.pop()
                                        val result = ShaderLabelNode()
                                        add(ShaderInsnNode(
                                            OP_EXT_INST,
                                            type.componentType,
                                            result,
                                            parent.builder.import("GLSL.std.450"),
                                            GLSL_LENGTH,
                                            self.label
                                        ))
                                        stack.push(StackValue.Label(result))
                                    }
                                    else -> TODO()
                                }
                                "lengthSquared" -> when (insn.desc) {
                                    "()$prim" -> {
                                        val self = stack.pop()
                                        val result = ShaderLabelNode()
                                        add(ShaderInsnNode(
                                            OP_DOT,
                                            type.componentType,
                                            result,
                                            self.label,
                                            self.label
                                        ))
                                        stack.push(StackValue.Label(result))
                                    }
                                    else -> TODO()
                                }
                                "normalize" -> when (insn.desc) {
                                    "()Lorg/joml/$name;" -> {
                                        val self = stack.pop()
                                        val result = ShaderLabelNode()
                                        add(ShaderInsnNode(
                                            OP_EXT_INST,
                                            type,
                                            result,
                                            parent.builder.import("GLSL.std.450"),
                                            GLSL_NORMALIZE,
                                            self.label
                                        ))
                                        vectorStoreLoad(result, self)
                                    }
                                    "(Lorg/joml/$name;)Lorg/joml/$name;" -> {
                                        val dest = stack.pop()
                                        val self = stack.pop()
                                        val result = ShaderLabelNode()
                                        add(ShaderInsnNode(
                                            OP_EXT_INST,
                                            type,
                                            result,
                                            parent.builder.import("GLSL.std.450"),
                                            GLSL_NORMALIZE,
                                            self.label
                                        ))
                                        vectorStoreLoad(result, dest)
                                    }

                                    else -> TODO()
                                }
                                "cross" -> when (insn.desc) {
                                    "(Lorg/joml/${name}c;)Lorg/joml/$name;" -> {
                                        val other = stack.pop()
                                        val self = stack.pop()
                                        val result = ShaderLabelNode()
                                        add(ShaderInsnNode(
                                            OP_EXT_INST,
                                            type,
                                            result,
                                            parent.builder.import("GLSL.std.450"),
                                            GLSL_CROSS,
                                            self.label,
                                            other.label
                                        ))
                                        vectorStoreLoad(result, self)
                                    }
                                    "(Lorg/joml/${name}c;Lorg/joml/$name;)Lorg/joml/$name;" -> {
                                        val dest = stack.pop()
                                        val other = stack.pop()
                                        val self = stack.pop()
                                        val result = ShaderLabelNode()
                                        add(ShaderInsnNode(
                                            OP_EXT_INST,
                                            type,
                                            result,
                                            parent.builder.import("GLSL.std.450"),
                                            GLSL_CROSS,
                                            self.label,
                                            other.label
                                        ))
                                        vectorStoreLoad(result, dest)
                                    }

                                    else -> TODO()
                                }
                                "<init>" -> {
                                    when (insn.desc) {
                                        "()V" -> {
                                            val vec = createVector(type, StackValue.Label(parent.builder.getConstant(ShaderConstant(ShaderBytecodeType.INT, listOf(0)).tryCast(type.componentType)!!)))
                                            val self = stack.pop() as StackValue.NewObject
                                            stack.replace(self, StackValue.Label(vec))
                                        }
                                        "(${prim})V" -> {
                                            val vec = createVector(type, stack.pop())
                                            val self = stack.pop() as StackValue.NewObject
                                            stack.replace(self, StackValue.Label(vec))
                                        }
                                        "(${fullPrim})V" -> {
                                            val vec = createVector(type, *stack.popVectorComponents(type))
                                            val self = stack.pop() as StackValue.NewObject
                                            stack.replace(self, StackValue.Label(vec))
                                        }
                                        "(Lorg/joml/${name}c;)V" -> {
                                            val vec = createVector(type, stack.pop())
                                            val self = stack.pop() as StackValue.NewObject
                                            stack.replace(self, StackValue.Label(vec))
                                        }
                                        else -> TODO()
                                    }
                                }
                                else -> TODO()
                            }
                        }

                        if (insn.owner == parent.node.name) {
                            val node = MethodPointer.method().name(insn.name).desc(insn.desc).findOrThrow(parent.node)
                            val func = parent.functions[node]!!
                            val args = Array(Type.getArgumentCount(insn.desc)) { stack.pop().label!! }.reversedArray()

                            if (stack.pop() != StackValue.This) {
                                throw AssertionError()
                            }

                            val result = ShaderLabelNode()
                            add(func.call(result, *args))

                            if (Type.getReturnType(insn.desc).sort != Type.VOID) {
                                stack.push(StackValue.Label(result))
                            }

                            continue
                        }

                        parent.getTypeHandler(Type.getObjectType(insn.owner))?.let {
                            it.handleMethodCall(this@JavaShaderMethodCompiler, insn)
                            continue
                        }

                        TODO()
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
                                    throw JavaShaderCompilationException("Cannot create arrays of dynamic size")
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
                            Opcodes.NEW -> stack.pushNewObject()
                            Opcodes.CHECKCAST -> {
                                val v = stack.peek()

                                if (v is StackValue.LoadVariable) {
                                    v.variable.javaType?.let { type ->
                                        if (type.sort == Type.OBJECT) {
                                            val name = type.internalName

                                            if (name.equals("${insn.desc}c")) {
                                                throw JavaShaderCompilationException("Illegal cast from an immutable joml class $name to ${insn.desc}")
                                            }
                                        }
                                    }
                                }
                            }
                            else -> TODO("${insn.opcode}")
                        }
                        continue
                    }
                    is FieldInsnNode -> {
                        when (insn.opcode) {
                            Opcodes.GETFIELD -> {
                                val target = stack.pop()

                                if (target == StackValue.This) {
                                    parent.variables[insn.name]?.let { v ->
                                        stack.push(StackValue.LoadVariable(this, v))
                                        continue
                                    }
                                }

                                TODO("${insn.owner} ${insn.name} ${insn.desc}")
                            }
                            Opcodes.PUTFIELD -> {
                                val value = stack.pop()
                                val target = stack.pop()

                                if (target == StackValue.This) {
                                    parent.variables[insn.name]?.let { v ->
                                        add(ShaderInsnNode(OP_STORE, v.label, value.label!!))
                                        continue
                                    }
                                }

                                TODO("${insn.owner} ${insn.name} ${insn.desc}")
                            }
                        }
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
                                val index = stack.pop().label!!
                                val array = stack.pop() as StackValue.Array

                                val pointer = ShaderLabelNode()
                                val value = ShaderLabelNode()
                                add(ShaderInsnNode(OP_ACCESS_CHAIN, array.variable.type, pointer, array.variable.label, index))
                                add(ShaderInsnNode(OP_LOAD, array.variable.type.type.rootType, value, pointer))
                                stack.push(StackValue.Label(value))
                            }
                            Opcodes.IASTORE, Opcodes.LASTORE, Opcodes.FASTORE, Opcodes.DASTORE, Opcodes.AASTORE, Opcodes.BASTORE, Opcodes.CASTORE, Opcodes.SASTORE -> {
                                val value = stack.pop().label!!
                                val index = stack.pop().label!!
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
                            Opcodes.IRETURN, Opcodes.LRETURN, Opcodes.FRETURN, Opcodes.DRETURN, Opcodes.ARETURN -> add(ShaderInsnNode(OP_RETURN_VALUE, stack.pop().label!!))

                            else -> TODO("${insn.opcode}")
                        }
                        continue
                    }
                }

                TODO("unsupported op $insn ${insn.opcode}")
            }

            if (!stack.isEmpty()) {
                throw JavaShaderCompilationException("Stack is not empty at the end of method ${node.name}")
            }
        }
    }

    fun const(type: ShaderBytecodeType, value: Any) {
        stack.push(StackValue.Constant(parent.builder, ShaderConstant(type, listOf(value))))
    }

    fun cast(opcode: Int, to: ShaderBytecodeType) {
        val v = stack.pop()

        if (v is StackValue.Constant) {
            v.const.tryCast(to)?.let {
                stack.push(StackValue.Constant(v.builder, it))
                return
            }
        }

        val r = ShaderLabelNode()
        function.instructions.add(ShaderInsnNode(opcode, to, r, v.label))
        stack.push(StackValue.Label(r))
    }

    fun math(opcode: Int, result: ShaderBytecodeType) {
        val b = stack.pop().label!!
        val a = stack.pop().label!!
        val r = ShaderLabelNode()
        function.instructions.add(ShaderInsnNode(opcode, result, r, a, b))
        stack.push(StackValue.Label(r))
    }

    fun mathUnary(opcode: Int, result: ShaderBytecodeType) {
        val a = stack.pop().label!!
        val r = ShaderLabelNode()
        function.instructions.add(ShaderInsnNode(opcode, result, r, a))
        stack.push(StackValue.Label(r))
    }

    class Stack {
        private val contents = mutableListOf<StackValue>()
        private var newObjectIdCounter = 0

        fun clear() {
            contents.clear()
            newObjectIdCounter = 0
        }

        fun push(value: StackValue) {
            contents.add(value)
            println("${contents.size} pushed $value")
        }

        fun pushNewObject() {
            push(StackValue.NewObject(newObjectIdCounter++))
        }

        fun pop() = contents.removeLast().also { println("${contents.size} popped $it") }

        fun popVectorComponents(type: ShaderBytecodeType.Vector): Array<StackValue> = Array(type.componentCount) { pop() }.reversedArray()

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
        val label: ShaderLabelNode?
            get() = null

        data class Label(
            override val label: ShaderLabelNode
        ) : StackValue

        class LoadVariable(
            instructions: MutableList<ShaderInsnNode>,
            @JvmField
            val variable: ShaderVariable
        ) : StackValue {
            override val label: ShaderLabelNode by lazy {
                ShaderLabelNode().also { instructions.add(ShaderInsnNode(OP_LOAD, variable.type.type, it, variable.label)) }
            }

            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other !is LoadVariable) return false

                if (variable != other.variable) return false

                return true
            }

            override fun hashCode(): Int {
                return variable.hashCode()
            }

            override fun toString(): String {
                return "LoadVariable(variable=$variable)"
            }
        }

        data class Constant(
            @JvmField
            val builder: ShaderBytecodeBuilder,
            @JvmField
            val const: ShaderConstant
        ) : StackValue {
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

    sealed interface Local {
        val variable: ShaderVariable?
            get() = null
        val local: LocalVariableNode

        fun load(compiler: JavaShaderMethodCompiler): StackValue? = null

        data class Variable(
            override val variable: ShaderVariable,
            override val local: LocalVariableNode
        ) : Local {
            override fun load(compiler: JavaShaderMethodCompiler) = StackValue.LoadVariable(compiler.function.instructions, variable)
        }

        data class Argument(
            @JvmField
            val label: ShaderLabelNode,
            override val local: LocalVariableNode
        ) : Local {
            override fun load(compiler: JavaShaderMethodCompiler) = StackValue.Label(label)
        }

        data class NewArray(
            @JvmField
            val type: ShaderBytecodeType.Array,
            override val local: LocalVariableNode
        ) : Local

        data class This(
            override val local: LocalVariableNode
        ) : Local
    }
}