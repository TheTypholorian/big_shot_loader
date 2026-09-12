package net.typho.big_shot.loader.shaders.reflect

import net.typho.asm_util.method.MethodPointer
import net.typho.big_shot.loader.shaders.bytecode.*
import net.typho.big_shot.loader.util.ExpandingByteBuffer
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.IincInsnNode
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TypeInsnNode
import org.objectweb.asm.tree.VarInsnNode
import org.objectweb.asm.tree.analysis.Analyzer
import org.objectweb.asm.tree.analysis.BasicInterpreter
import org.objectweb.asm.tree.analysis.BasicValue
import org.objectweb.asm.tree.analysis.Frame
import kotlin.collections.get

class JavaShaderMethodCompiler(
    @JvmField
    val parent: JavaShaderCompiler,
    @JvmField
    val node: MethodNode,
    @JvmField
    val function: ShaderFunction<ShaderFunction.Instruction>
) {
    @JvmField
    val stack = Stack()
    @JvmField
    val locals = mutableMapOf<Int, Local>()
    private val jumpTargets = mutableMapOf<LabelNode, ShaderLabelNode>()

    fun loadLocal(index: Int, type: ShaderBytecodeType): Local {
        println("loading local $index")

        return if (type is ShaderBytecodeType.Array) {
            Local.NewArray(type)
        } else {
            val variable = ShaderVariable(ShaderBytecodeType.Pointer(STORAGE_CLASS_FUNCTION, type), ShaderLabelNode())
            function.instructions.add(ShaderInsnNode(OP_VARIABLE, variable.type, variable.label, variable.type.storageClass, variable.initializer))
            Local.Variable(variable)
        }
    }

    fun getOrLoadLocal(index: Int, type: ShaderBytecodeType) = locals.computeIfAbsent(index) { loadLocal(index, type) }

    fun compile() {
        stack.clear()
        locals.clear()
        jumpTargets.clear()

        //frames = Analyzer(BasicInterpreter()).analyze(parent.node.name, node)

        val static = node.access and Opcodes.ACC_STATIC != 0

        if (!static) {
            locals[0] = Local.This
        }

        function.instructions.clear()
        function.instructions.apply {
            Type.getArgumentTypes(node.desc).forEachIndexed { index, type ->
                val type = ShaderBytecodeType.convertJavaType(type)
                val label = ShaderLabelNode()
                function.instructions.add(ShaderInsnNode(OP_FUNCTION_PARAMETER, type, label))
                locals[if (static) index else index + 1] = Local.Argument(label, type)
            }

            function.instructions.add(ShaderInsnNode(OP_LABEL, ShaderLabelNode()))

            node.instructions.forEachIndexed { insnIndex, insn ->
                when (insn.opcode) {
                    -1 -> {
                        if (insn is LabelNode) {
                            //jumpTargets.computeIfAbsent(insn) { ShaderLabelNode() }?.let {
                            jumpTargets[insn]?.let {
                                add(ShaderInsnNode(OP_BRANCH, it))
                                add(ShaderInsnNode(OP_LABEL, it))
                            }
                        }
                    }

                    Opcodes.NOP -> add(ShaderInsnNode(OP_NO_OP))

                    Opcodes.ACONST_NULL -> throw JavaShaderCompilationException("Nullability isn't supported")

                    Opcodes.ICONST_M1 -> const(ShaderBytecodeType.INT, -1)
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

                    Opcodes.BIPUSH, Opcodes.SIPUSH -> const(ShaderBytecodeType.INT, (insn as IntInsnNode).operand)
                    Opcodes.LDC -> when (val const = (insn as LdcInsnNode).cst) {
                        is Boolean -> const(ShaderBytecodeType.Bool, const)
                        is Byte -> const(ShaderBytecodeType.BYTE, const)
                        is Short -> const(ShaderBytecodeType.SHORT, const)
                        is Int -> const(ShaderBytecodeType.INT, const)
                        is Long -> const(ShaderBytecodeType.LONG, const)
                        is Float -> const(ShaderBytecodeType.FLOAT, const)
                        is Double -> const(ShaderBytecodeType.DOUBLE, const)
                        is String -> stack.push(StackValue.StringConstant(const))
                        else -> throw JavaShaderCompilationException("Unsupported constant $const")
                    }

                    Opcodes.ILOAD, Opcodes.LLOAD, Opcodes.FLOAD, Opcodes.DLOAD, Opcodes.ALOAD -> {
                        val local = locals[(insn as VarInsnNode).`var`]!!

                        if (local is Local.This) {
                            stack.push(StackValue.This)
                        } else {
                            stack.push(local.load(this@JavaShaderMethodCompiler)!!)
                        }
                    }
                    Opcodes.IALOAD, Opcodes.LALOAD, Opcodes.FALOAD, Opcodes.DALOAD, Opcodes.AALOAD, Opcodes.BALOAD, Opcodes.CALOAD, Opcodes.SALOAD -> {
                        val index = stack.pop().label!!
                        val array = stack.pop() as StackValue.Array

                        val pointer = ShaderLabelNode()
                        val value = ShaderLabelNode()
                        add(ShaderInsnNode(OP_ACCESS_CHAIN, array.variable.type, pointer, array.variable.label, index))
                        add(ShaderInsnNode(OP_LOAD, array.variable.type.rootType, value, pointer))
                        stack.push(StackValue.Label(value, array.variable.type.rootType))
                    }

                    Opcodes.ISTORE, Opcodes.LSTORE, Opcodes.FSTORE, Opcodes.DSTORE, Opcodes.ASTORE -> {
                        insn as VarInsnNode

                        when (val value = stack.pop()) {
                            is StackValue.Array -> {
                                val local = getOrLoadLocal(insn.`var`, value.type)

                                if (local !is Local.NewArray) {
                                    TODO("reassigning arrays?")
                                }

                                locals[insn.`var`] = Local.Variable(value.variable)
                            }
                            // TODO
                            /*
                            is StackValue.LoadVariable -> if (value.variable.type.type is ShaderBytecodeType.Vector) {
                                throw JavaShaderCompilationException("Cannot store a mutable ${value.variable.type.type} value from one variable in another, since joml vectors are mutable while glsl vectors are immutable.")
                            }
                             */
                            else -> getOrLoadLocal(insn.`var`, value.type!!).store(this@JavaShaderMethodCompiler, value)!!
                        }
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
                    Opcodes.SWAP -> stack.swap()

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

                    Opcodes.IINC -> {
                        insn as IincInsnNode
                        val value = parent.builder.getConstant(ShaderConstant(ShaderBytecodeType.INT, listOf(insn.incr)))
                        val local = getOrLoadLocal(insn.`var`, ShaderBytecodeType.INT)
                        val temp = local.load(this@JavaShaderMethodCompiler)!!
                        val result = ShaderLabelNode()

                        function.instructions.add(ShaderInsnNode(OP_I_ADD, ShaderBytecodeType.INT /* TODO */, result, temp.label!!, value))
                        local.store(this@JavaShaderMethodCompiler, StackValue.Label(result, ShaderBytecodeType.INT))!!
                    }

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

                    Opcodes.LCMP, Opcodes.FCMPL, Opcodes.FCMPG, Opcodes.DCMPL, Opcodes.DCMPG -> stack.push(StackValue.Comparison(insn.opcode, stack.pop(), stack.pop()))

                    Opcodes.IFEQ -> jump(OP_I_NOT_EQUAL, OP_F_ORD_NOT_EQUAL, (insn as JumpInsnNode).label)
                    Opcodes.IFNE -> jump(OP_I_EQUAL, OP_F_ORD_EQUAL, (insn as JumpInsnNode).label)
                    Opcodes.IFLT -> jump(OP_S_GREATER_THAN_EQUAL, OP_F_ORD_GREATER_THAN_EQUAL, (insn as JumpInsnNode).label)
                    Opcodes.IFGE -> jump(OP_S_LESS_THAN, OP_F_ORD_LESS_THAN, (insn as JumpInsnNode).label)
                    Opcodes.IFGT -> jump(OP_S_LESS_THAN_EQUAL, OP_F_ORD_LESS_THAN_EQUAL, (insn as JumpInsnNode).label)
                    Opcodes.IFLE -> jump(OP_S_GREATER_THAN, OP_F_ORD_GREATER_THAN, (insn as JumpInsnNode).label)

                    Opcodes.IF_ICMPEQ -> jump(OP_I_NOT_EQUAL, (insn as JumpInsnNode).label, stack.pop().label!!)
                    Opcodes.IF_ICMPNE -> jump(OP_I_EQUAL, (insn as JumpInsnNode).label, stack.pop().label!!)
                    Opcodes.IF_ICMPLT -> jump(OP_S_GREATER_THAN_EQUAL, (insn as JumpInsnNode).label, stack.pop().label!!)
                    Opcodes.IF_ICMPGE -> jump(OP_S_LESS_THAN, (insn as JumpInsnNode).label, stack.pop().label!!)
                    Opcodes.IF_ICMPGT -> jump(OP_S_LESS_THAN_EQUAL, (insn as JumpInsnNode).label, stack.pop().label!!)
                    Opcodes.IF_ICMPLE -> jump(OP_S_GREATER_THAN, (insn as JumpInsnNode).label, stack.pop().label!!)

                    Opcodes.GOTO -> jump((insn as JumpInsnNode).label)

                    // TODO comparison ops
                    // TODO jump ops
                    // TODO RET
                    // TODO switches

                    Opcodes.IRETURN, Opcodes.LRETURN, Opcodes.FRETURN, Opcodes.DRETURN, Opcodes.ARETURN -> add(ShaderInsnNode(OP_RETURN_VALUE, stack.pop().label!!))
                    Opcodes.RETURN -> add(ShaderInsnNode(OP_RETURN))

                    Opcodes.GETFIELD -> {
                        insn as FieldInsnNode

                        parent.getTypeHandler(Type.getObjectType(insn.owner))?.let {
                            it.handleFieldOp(this@JavaShaderMethodCompiler, insn)
                            return@forEachIndexed
                        }

                        val target = stack.pop()

                        if (target == StackValue.This) {
                            parent.variables[insn.name]?.let { v ->
                                stack.push(StackValue.LoadVariable(this, v))
                                return@forEachIndexed
                            }
                        }

                        TODO("${insn.owner} ${insn.name} ${insn.desc}")
                    }
                    Opcodes.PUTFIELD -> {
                        insn as FieldInsnNode

                        parent.getTypeHandler(Type.getObjectType(insn.owner))?.let {
                            it.handleFieldOp(this@JavaShaderMethodCompiler, insn)
                            return@forEachIndexed
                        }

                        val value = stack.pop()
                        val target = stack.pop()

                        if (target == StackValue.This) {
                            parent.variables[insn.name]?.let { v ->
                                add(ShaderInsnNode(OP_STORE, v.label, value.label!!))
                                return@forEachIndexed
                            }
                        }

                        TODO("${insn.owner} ${insn.name} ${insn.desc}")
                    }
                    // TODO static fields

                    Opcodes.INVOKEVIRTUAL, Opcodes.INVOKESPECIAL, Opcodes.INVOKESTATIC, Opcodes.INVOKEINTERFACE, Opcodes.INVOKEDYNAMIC -> {
                        insn as MethodInsnNode

                        if (insn.owner == parent.node.name) {
                            val node = MethodPointer.method().name(insn.name).desc(insn.desc).findOrThrow(parent.node)
                            val func = parent.functions[node]!!
                            val args = Array(Type.getArgumentCount(insn.desc)) { stack.pop().label!! }.reversedArray()

                            if (stack.pop() != StackValue.This) {
                                throw AssertionError()
                            }

                            val result = ShaderLabelNode()
                            add(func.call(result, *args))

                            if (func.type.returnType != ShaderBytecodeType.Void) {
                                stack.push(StackValue.Label(result, func.type.returnType))
                            }

                            return@forEachIndexed
                        }

                        parent.getTypeHandler(Type.getObjectType(insn.owner))?.let {
                            it.handleMethodCall(this@JavaShaderMethodCompiler, insn)
                            return@forEachIndexed
                        }
                    }

                    Opcodes.NEW -> stack.pushNewObject()
                    Opcodes.NEWARRAY -> {
                        val length = stack.pop()

                        if (length !is StackValue.Constant) {
                            throw JavaShaderCompilationException("Cannot create arrays of dynamic size")
                        }

                        val type = when ((insn as IntInsnNode).operand) {
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
                    // TODO ANEWARRAY
                    // TODO ARRAYLENGTH
                    // TODO ATHROW
                    Opcodes.CHECKCAST -> parent.getTypeHandler(Type.getObjectType((insn as TypeInsnNode).desc))?.handleCastFrom(this@JavaShaderMethodCompiler, stack.peek()!!)
                    // TODO INSTANCEOF
                    // TODO synchronization
                    // TODO MULTIANEWARRAY
                    // TODO null jumps

                    else -> TODO("${insn.opcode}")
                }
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
        stack.push(StackValue.Label(r, to))
    }

    fun math(opcode: Int, result: ShaderBytecodeType) {
        val b = stack.pop().label!!
        val a = stack.pop().label!!
        val r = ShaderLabelNode()
        function.instructions.add(ShaderInsnNode(opcode, result, r, a, b))
        stack.push(StackValue.Label(r, result))
    }

    fun mathUnary(opcode: Int, result: ShaderBytecodeType) {
        val a = stack.pop().label!!
        val r = ShaderLabelNode()
        function.instructions.add(ShaderInsnNode(opcode, result, r, a))
        stack.push(StackValue.Label(r, result))
    }

    fun jump(comparisonOpcode: Int, floatComparisonOpcode: Int, target: LabelNode) {
        val target = jumpTargets.computeIfAbsent(target) { ShaderLabelNode() }
        val value = stack.pop()
        val bool = ShaderLabelNode()

        function.instructions.add(if (value is StackValue.Comparison) {
            when (value.javaOpcode) {
                Opcodes.LCMP -> ShaderInsnNode(comparisonOpcode, ShaderBytecodeType.Bool, bool, value.left.label!!, value.right.label!!)
                // TODO proper handling for NaNs
                Opcodes.FCMPL, Opcodes.DCMPL -> ShaderInsnNode(floatComparisonOpcode, ShaderBytecodeType.Bool, bool, value.left.label!!, value.right.label!!)
                Opcodes.FCMPG, Opcodes.DCMPG -> ShaderInsnNode(floatComparisonOpcode, ShaderBytecodeType.Bool, bool, value.left.label!!, value.right.label!!)
                else -> throw AssertionError()
            }
        } else {
            ShaderInsnNode(comparisonOpcode, ShaderBytecodeType.Bool, bool, value.label!!, parent.builder.getConstant(ShaderConstant(ShaderBytecodeType.INT, listOf(0))))
        })

        val body = ShaderLabelNode()
        function.instructions.add(ShaderInsnNode(OP_SELECTION_MERGE, target, SELECTION_CONTROL_NONE))
        function.instructions.add(ShaderInsnNode(OP_BRANCH_CONDITIONAL, bool, body, target))
        function.instructions.add(ShaderInsnNode(OP_LABEL, body))
    }

    fun jump(comparisonOpcode: Int, target: LabelNode, right: ShaderLabelNode) {
        val target = jumpTargets.computeIfAbsent(target) { ShaderLabelNode() }
        val left = stack.pop()
        val bool = ShaderLabelNode()
        function.instructions.add(ShaderInsnNode(comparisonOpcode, ShaderBytecodeType.Bool, bool, left.label!!, right))
        val body = ShaderLabelNode()
        function.instructions.add(ShaderInsnNode(OP_SELECTION_MERGE, target, SELECTION_CONTROL_NONE))
        function.instructions.add(ShaderInsnNode(OP_BRANCH_CONDITIONAL, bool, body, target))
        function.instructions.add(ShaderInsnNode(OP_LABEL, body))
    }

    fun jump(target: LabelNode) {
        val target = jumpTargets.computeIfAbsent(target) { ShaderLabelNode() } // TODO the target label might already have been written
        function.instructions.add(ShaderInsnNode(OP_BRANCH, target))
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

        fun swap() {
            val last = contents.removeLast()
            contents.add(contents.size - 1, last)
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
        val type: ShaderBytecodeType?
            get() = null

        data class Label(
            override val label: ShaderLabelNode,
            override val type: ShaderBytecodeType?
        ) : StackValue

        class LoadVariable(
            instructions: MutableList<ShaderFunction.Instruction>,
            @JvmField
            val variable: ShaderVariable
        ) : StackValue {
            override val label: ShaderLabelNode by lazy {
                ShaderLabelNode().also { instructions.add(ShaderInsnNode(OP_LOAD, variable.type.type, it, variable.label)) }
            }
            override val type: ShaderBytecodeType
                get() = variable.type

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
            override val type: ShaderBytecodeType
                get() = const.type
        }

        data class StringConstant(
            @JvmField
            val const: String
        ) : StackValue

        data class Comparison(
            @JvmField
            val javaOpcode: Int,
            @JvmField
            val right: StackValue,
            @JvmField
            val left: StackValue
        ) : StackValue

        data class Array(
            @JvmField
            val variable: ShaderVariable
        ) : StackValue {
            override val type: ShaderBytecodeType
                get() = variable.type
        }

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
        fun load(compiler: JavaShaderMethodCompiler): StackValue? = null

        fun store(compiler: JavaShaderMethodCompiler, value: StackValue): Unit? = null

        data class Variable(
            @JvmField
            val variable: ShaderVariable
        ) : Local {
            override fun load(compiler: JavaShaderMethodCompiler) = StackValue.LoadVariable(compiler.function.instructions, variable)

            override fun store(compiler: JavaShaderMethodCompiler, value: StackValue) {
                compiler.function.instructions.add(ShaderInsnNode(OP_STORE, variable.label, value.label!!))
            }
        }

        data class Argument(
            @JvmField
            val label: ShaderLabelNode,
            @JvmField
            val type: ShaderBytecodeType
        ) : Local {
            override fun load(compiler: JavaShaderMethodCompiler) = StackValue.Label(label, type)

            override fun store(compiler: JavaShaderMethodCompiler, value: StackValue) {
                throw JavaShaderCompilationException("Cannot modify an argument's value, create a new variable.")
            }
        }

        data class NewArray(
            @JvmField
            val type: ShaderBytecodeType.Array
        ) : Local

        object This : Local
    }
}