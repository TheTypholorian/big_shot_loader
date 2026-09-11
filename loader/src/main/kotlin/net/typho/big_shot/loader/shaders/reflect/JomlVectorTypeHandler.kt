package net.typho.big_shot.loader.shaders.reflect

import net.typho.big_shot.loader.shaders.bytecode.*
import net.typho.big_shot.loader.shaders.reflect.JavaShaderMethodCompiler.StackValue
import org.objectweb.asm.Type
import org.objectweb.asm.tree.LocalVariableNode
import org.objectweb.asm.tree.MethodInsnNode

abstract class JomlVectorTypeHandler(
    @JvmField
    val mutableClassType: Type,
    @JvmField
    val immutableClassType: Type,
    @JvmField
    val componentType: Type,
    @JvmField
    val mutable: Boolean,
    @JvmField
    val type: ShaderBytecodeType.Vector
) : JavaShaderTypeHandler {
    object Double2 : DoubleVector(Type.getType("Lorg/joml/Vector2d;"), Type.getType("Lorg/joml/Vector2dc;"), true, ShaderBytecodeType.VECTOR2D)
    object Double3 : DoubleVector(Type.getType("Lorg/joml/Vector3d;"), Type.getType("Lorg/joml/Vector3dc;"), true, ShaderBytecodeType.VECTOR3D)
    object Double4 : DoubleVector(Type.getType("Lorg/joml/Vector4d;"), Type.getType("Lorg/joml/Vector4dc;"), true, ShaderBytecodeType.VECTOR4D)

    object Float2 : FloatVector(Type.getType("Lorg/joml/Vector2f;"), Type.getType("Lorg/joml/Vector2fc;"), true, ShaderBytecodeType.VECTOR2F)
    object Float3 : FloatVector(Type.getType("Lorg/joml/Vector3f;"), Type.getType("Lorg/joml/Vector3fc;"), true, ShaderBytecodeType.VECTOR3F)
    object Float4 : FloatVector(Type.getType("Lorg/joml/Vector4f;"), Type.getType("Lorg/joml/Vector4fc;"), true, ShaderBytecodeType.VECTOR4F)

    object Long2 : LongVector(Type.getType("Lorg/joml/Vector2L;"), Type.getType("Lorg/joml/Vector2Lc;"), true, ShaderBytecodeType.VECTOR2L)
    object Long3 : LongVector(Type.getType("Lorg/joml/Vector3L;"), Type.getType("Lorg/joml/Vector3Lc;"), true, ShaderBytecodeType.VECTOR3L)
    object Long4 : LongVector(Type.getType("Lorg/joml/Vector4L;"), Type.getType("Lorg/joml/Vector4Lc;"), true, ShaderBytecodeType.VECTOR4L)

    object Int2 : IntVector(Type.getType("Lorg/joml/Vector2i;"), Type.getType("Lorg/joml/Vector2ic;"), true, ShaderBytecodeType.VECTOR2I)
    object Int3 : IntVector(Type.getType("Lorg/joml/Vector3i;"), Type.getType("Lorg/joml/Vector3ic;"), true, ShaderBytecodeType.VECTOR3I)
    object Int4 : IntVector(Type.getType("Lorg/joml/Vector4i;"), Type.getType("Lorg/joml/Vector4ic;"), true, ShaderBytecodeType.VECTOR4I)

    object Double2c : DoubleVector(Type.getType("Lorg/joml/Vector2d;"), Type.getType("Lorg/joml/Vector2dc;"), false, ShaderBytecodeType.VECTOR2D)
    object Double3c : DoubleVector(Type.getType("Lorg/joml/Vector3d;"), Type.getType("Lorg/joml/Vector3dc;"), false, ShaderBytecodeType.VECTOR3D)
    object Double4c : DoubleVector(Type.getType("Lorg/joml/Vector4d;"), Type.getType("Lorg/joml/Vector4dc;"), false, ShaderBytecodeType.VECTOR4D)

    object Float2c : FloatVector(Type.getType("Lorg/joml/Vector2f;"), Type.getType("Lorg/joml/Vector2fc;"), false, ShaderBytecodeType.VECTOR2F)
    object Float3c : FloatVector(Type.getType("Lorg/joml/Vector3f;"), Type.getType("Lorg/joml/Vector3fc;"), false, ShaderBytecodeType.VECTOR3F)
    object Float4c : FloatVector(Type.getType("Lorg/joml/Vector4f;"), Type.getType("Lorg/joml/Vector4fc;"), false, ShaderBytecodeType.VECTOR4F)

    object Long2c : LongVector(Type.getType("Lorg/joml/Vector2L;"), Type.getType("Lorg/joml/Vector2Lc;"), false, ShaderBytecodeType.VECTOR2L)
    object Long3c : LongVector(Type.getType("Lorg/joml/Vector3L;"), Type.getType("Lorg/joml/Vector3Lc;"), false, ShaderBytecodeType.VECTOR3L)
    object Long4c : LongVector(Type.getType("Lorg/joml/Vector4L;"), Type.getType("Lorg/joml/Vector4Lc;"), false, ShaderBytecodeType.VECTOR4L)

    object Int2c : IntVector(Type.getType("Lorg/joml/Vector2i;"), Type.getType("Lorg/joml/Vector2ic;"), false, ShaderBytecodeType.VECTOR2I)
    object Int3c : IntVector(Type.getType("Lorg/joml/Vector3i;"), Type.getType("Lorg/joml/Vector3ic;"), false, ShaderBytecodeType.VECTOR3I)
    object Int4c : IntVector(Type.getType("Lorg/joml/Vector4i;"), Type.getType("Lorg/joml/Vector4ic;"), false, ShaderBytecodeType.VECTOR4I)

    companion object : JavaShaderTypeHandler.Supplier {
        @JvmStatic
        fun getDouble(count: Int) = when (count) {
            2 -> Double2
            3 -> Double3
            4 -> Double4
            else -> throw IllegalArgumentException(count.toString())
        }

        @JvmStatic
        fun getFloat(count: Int) = when (count) {
            2 -> Float2
            3 -> Float3
            4 -> Float4
            else -> throw IllegalArgumentException(count.toString())
        }

        @JvmStatic
        fun getLong(count: Int) = when (count) {
            2 -> Long2
            3 -> Long3
            4 -> Long4
            else -> throw IllegalArgumentException(count.toString())
        }

        @JvmStatic
        fun getInt(count: Int) = when (count) {
            2 -> Int2
            3 -> Int3
            4 -> Int4
            else -> throw IllegalArgumentException(count.toString())
        }

        @JvmStatic
        fun getDoublec(count: Int) = when (count) {
            2 -> Double2c
            3 -> Double3c
            4 -> Double4c
            else -> throw IllegalArgumentException(count.toString())
        }

        @JvmStatic
        fun getFloatc(count: Int) = when (count) {
            2 -> Float2c
            3 -> Float3c
            4 -> Float4c
            else -> throw IllegalArgumentException(count.toString())
        }

        @JvmStatic
        fun getLongc(count: Int) = when (count) {
            2 -> Long2c
            3 -> Long3c
            4 -> Long4c
            else -> throw IllegalArgumentException(count.toString())
        }

        @JvmStatic
        fun getIntc(count: Int) = when (count) {
            2 -> Int2c
            3 -> Int3c
            4 -> Int4c
            else -> throw IllegalArgumentException(count.toString())
        }

        override fun getTypeHandler(type: Type): JavaShaderTypeHandler? {
            return when (type.internalName) {
                "org/joml/Vector2d" -> Double2
                "org/joml/Vector3d" -> Double3
                "org/joml/Vector4d" -> Double4
                "org/joml/Vector2f" -> Float2
                "org/joml/Vector3f" -> Float3
                "org/joml/Vector4f" -> Float4
                "org/joml/Vector2L" -> Long2
                "org/joml/Vector3L" -> Long3
                "org/joml/Vector4L" -> Long4
                "org/joml/Vector2i" -> Int2
                "org/joml/Vector3i" -> Int3
                "org/joml/Vector4i" -> Int4
                "org/joml/Vector2dc" -> Double2c
                "org/joml/Vector3dc" -> Double3c
                "org/joml/Vector4dc" -> Double4c
                "org/joml/Vector2fc" -> Float2c
                "org/joml/Vector3fc" -> Float3c
                "org/joml/Vector4fc" -> Float4c
                "org/joml/Vector2Lc" -> Long2c
                "org/joml/Vector3Lc" -> Long3c
                "org/joml/Vector4Lc" -> Long4c
                "org/joml/Vector2ic" -> Int2c
                "org/joml/Vector3ic" -> Int3c
                "org/joml/Vector4ic" -> Int4c
                else -> null
            }
        }

        fun JavaShaderMethodCompiler.createVector(type: ShaderBytecodeType.Vector, value: StackValue): ShaderLabelNode {
            return createVector(type, *Array(type.componentCount) { value })
        }

        fun JavaShaderMethodCompiler.createVector(type: ShaderBytecodeType.Vector, vararg values: StackValue): ShaderLabelNode {
            return if (values.all { it is StackValue.Constant }) {
                parent.builder.getConstant(ShaderConstant(type, values.map { it.label!! }))
            } else {
                val result = ShaderLabelNode()
                function.instructions.add(ShaderInsnNode(OP_COMPOSITE_CONSTRUCT, type, result, values.map { it.label }))
                result
            }
        }

        fun JavaShaderMethodCompiler.vectorStoreLoad(result: ShaderLabelNode, dest: StackValue) {
            if (dest is StackValue.LoadVariable) {
                function.instructions.add(ShaderInsnNode(OP_STORE, dest.variable.label, result))
                stack.push(StackValue.LoadVariable(function.instructions, dest.variable))
            } else {
                stack.push(StackValue.Label(result))
            }
        }

        fun JavaShaderMethodCompiler.vectorOp(opcode: Int, type: ShaderBytecodeType.Vector, dest: StackValue, add: ShaderLabelNode, self: ShaderLabelNode) {
            val result = ShaderLabelNode()
            function.instructions.add(ShaderInsnNode(opcode, type, result, self, add))
            vectorStoreLoad(result, dest)
        }

        fun JavaShaderMethodCompiler.vectorOpSelf(opcode: Int, type: ShaderBytecodeType.Vector, add: ShaderLabelNode, self: StackValue) {
            vectorOp(opcode, type, self, add, self.label!!)
        }

        fun JavaShaderMethodCompiler.vectorInit(type: ShaderBytecodeType.Vector, value: StackValue) {
            vectorInit(type, *Array(type.componentCount) { value })
        }

        fun JavaShaderMethodCompiler.vectorInit(type: ShaderBytecodeType.Vector, vararg values: StackValue) {
            val vec = createVector(type, *values)
            val self = stack.pop() as StackValue.NewObject
            stack.replace(self, StackValue.Label(vec))
        }
    }

    val classType: Type
        get() = if (mutable) mutableClassType else immutableClassType

    @JvmField
    val voidSinglePrimDesc = Type.getMethodDescriptor(Type.VOID_TYPE, componentType)
    @JvmField
    val voidPrimDesc = Type.getMethodDescriptor(Type.VOID_TYPE, *Array(type.componentCount) { componentType })
    @JvmField
    val voidImmutableDesc = Type.getMethodDescriptor(Type.VOID_TYPE, immutableClassType)
    @JvmField
    val voidPrimArrayDesc = Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType("[$componentType"))

    @JvmField
    val opSinglePrimDestDesc = Type.getMethodDescriptor(mutableClassType, componentType, mutableClassType)
    @JvmField
    val opPrimDestDesc = Type.getMethodDescriptor(mutableClassType, *Array(type.componentCount) { componentType }, mutableClassType)
    @JvmField
    val opImmutableDestDesc = Type.getMethodDescriptor(mutableClassType, immutableClassType, mutableClassType)
    @JvmField
    val opSinglePrimSelfDesc = Type.getMethodDescriptor(mutableClassType, componentType)
    @JvmField
    val opPrimSelfDesc = Type.getMethodDescriptor(mutableClassType, *Array(type.componentCount) { componentType })
    @JvmField
    val opImmutableSelfDesc = Type.getMethodDescriptor(mutableClassType, immutableClassType)

    override fun handleMethodCall(
        compiler: JavaShaderMethodCompiler,
        call: MethodInsnNode
    ) {
        val success = when (call.name) {
            "<init>" -> handleConstructor(compiler, call.desc)
            "add" -> handleSimpleOp(compiler, if (type.componentType is ShaderBytecodeType.Integer) OP_I_ADD else OP_F_ADD, call.desc)
            "sub" -> handleSimpleOp(compiler, if (type.componentType is ShaderBytecodeType.Integer) OP_I_SUB else OP_F_SUB, call.desc)
            "mul" -> handleSimpleOp(compiler, if (type.componentType is ShaderBytecodeType.Integer) OP_I_MUL else OP_F_MUL, call.desc)
            "div" -> handleSimpleOp(compiler, if (type.componentType is ShaderBytecodeType.Integer) OP_S_DIV else OP_F_DIV, call.desc)
            else -> false
        }

        if (!success) {
            throw JavaShaderCompilationException("Unsupported method ${call.owner}.${call.name}${call.desc}")
        }
    }

    override fun createLocalVariable(
        compiler: JavaShaderMethodCompiler,
        local: LocalVariableNode,
        javaType: Type,
        type: ShaderBytecodeType
    ): ShaderVariable? {
        return null
    }

    protected open fun handleSimpleOp(compiler: JavaShaderMethodCompiler, opcode: Int, desc: String): Boolean {
        when (desc) {
            opSinglePrimDestDesc -> compiler.vectorOp(opcode, type, compiler.stack.pop(), compiler.createVector(type, compiler.stack.pop()), compiler.stack.pop().label!!)
            opPrimDestDesc -> compiler.vectorOp(opcode, type, compiler.stack.pop(), compiler.createVector(type, *compiler.stack.popVectorComponents(type)), compiler.stack.pop().label!!)
            opImmutableDestDesc -> compiler.vectorOp(opcode, type, compiler.stack.pop(), compiler.stack.pop().label!!, compiler.stack.pop().label!!)

            opSinglePrimSelfDesc -> compiler.vectorOpSelf(opcode, type, compiler.createVector(type, compiler.stack.pop()), compiler.stack.pop())
            opPrimSelfDesc -> compiler.vectorOpSelf(opcode, type, compiler.createVector(type, *compiler.stack.popVectorComponents(type)), compiler.stack.pop())
            opImmutableSelfDesc -> compiler.vectorOpSelf(opcode, type, compiler.stack.pop().label!!, compiler.stack.pop())

            else -> return false
        }

        return true
    }

    protected open fun handleNoArgVector(compiler: JavaShaderMethodCompiler) {
        val zero = StackValue.Label(compiler.parent.builder.getConstant((type.componentType as ShaderBytecodeType.Numerical).getConstant(0)))

        if (type.componentCount == 4) {
            val one = StackValue.Label(compiler.parent.builder.getConstant(type.componentType.getConstant(1)))
            compiler.vectorInit(type, zero, zero, zero, one)
        } else {
            compiler.vectorInit(type, zero)
        }
    }

    protected abstract fun castConstructorType(compiler: JavaShaderMethodCompiler, arg: Type, input: StackValue, type: ShaderBytecodeType.Vector): StackValue?

    protected open fun handleConstructor(compiler: JavaShaderMethodCompiler, desc: String): Boolean {
        when (desc) {
            "()V" -> handleNoArgVector(compiler)
            voidSinglePrimDesc -> compiler.vectorInit(type, compiler.stack.pop())
            voidPrimDesc -> compiler.vectorInit(type, *compiler.stack.popVectorComponents(type))
            voidImmutableDesc -> compiler.vectorInit(type, compiler.stack.pop())
            voidPrimArrayDesc -> {
                val array = compiler.stack.pop() as StackValue.LoadVariable
                val components = Array(type.componentCount) { index ->
                    val pointer = ShaderLabelNode()
                    val value = ShaderLabelNode()
                    compiler.function.instructions.add(ShaderInsnNode(OP_ACCESS_CHAIN, array.variable.type, pointer, array.variable.label, index))
                    compiler.function.instructions.add(ShaderInsnNode(OP_LOAD, array.variable.type.type.rootType, value, pointer))
                    StackValue.Label(value)
                }
                compiler.vectorInit(type, *components)
            }
            else -> {
                val args = Type.getArgumentTypes(desc)

                when (args.size) {
                    1 -> {
                        if (type.componentCount == 2) {
                            when (args[0]) {
                                Double3c.classType, Float3c.classType, Int3c.classType -> { // TODO abstractify
                                    val targetLabel = ShaderLabelNode()
                                    compiler.function.instructions.add(ShaderInsnNode(OP_VECTOR_SHUFFLE, type, targetLabel, compiler.stack.pop().label!!, 0, 1))
                                    compiler.vectorInit(type, StackValue.Label(targetLabel))
                                    return true
                                }
                            }
                        }

                        val input = compiler.stack.pop()
                        val processed = castConstructorType(compiler, args[0], input, type) ?: return false
                        compiler.vectorInit(type, processed)
                        return true
                    }
                    2 -> {
                        if (args[1] == componentType && type.componentCount > 2) {
                            val z = compiler.stack.pop()
                            val xy = compiler.stack.pop()

                            val processed = castConstructorType(compiler, args[0], xy, type.copy(componentCount = type.componentCount - 1)) ?: return false
                            compiler.vectorInit(type, processed, z)
                            return true
                        }
                    }
                    3 -> {
                        if (args[1] == componentType && args[2] == componentType && type.componentCount > 3) {
                            val w = compiler.stack.pop()
                            val z = compiler.stack.pop()
                            val xy = compiler.stack.pop()

                            val processed = castConstructorType(compiler, args[0], xy, type.copy(componentCount = type.componentCount - 2)) ?: return false
                            compiler.vectorInit(type, processed, z, w)
                            return true
                        }
                    }
                }

                return false
            }
        }

        return true
    }

    open class DoubleVector(
        mutableClassType: Type,
        immutableClassType: Type,
        mutable: Boolean,
        type: ShaderBytecodeType.Vector
    ) : JomlVectorTypeHandler(mutableClassType, immutableClassType, Type.DOUBLE_TYPE, mutable, type) {
        override fun castConstructorType(
            compiler: JavaShaderMethodCompiler,
            arg: Type,
            input: StackValue,
            type: ShaderBytecodeType.Vector
        ): StackValue? {
            return when (arg) {
                getDoublec(type.componentCount).classType -> input
                getIntc(type.componentCount).classType -> {
                    val targetLabel = ShaderLabelNode()
                    compiler.function.instructions.add(ShaderInsnNode(OP_CONVERT_S_TO_F, type, targetLabel, input.label))
                    StackValue.Label(targetLabel)
                }
                else -> null
            }
        }
    }

    open class FloatVector(
        mutableClassType: Type,
        immutableClassType: Type,
        mutable: Boolean,
        type: ShaderBytecodeType.Vector
    ) : JomlVectorTypeHandler(mutableClassType, immutableClassType, Type.FLOAT_TYPE, mutable, type) {
        override fun castConstructorType(
            compiler: JavaShaderMethodCompiler,
            arg: Type,
            input: StackValue,
            type: ShaderBytecodeType.Vector
        ): StackValue? {
            return when (arg) {
                getFloatc(type.componentCount).classType -> input
                getDoublec(type.componentCount).classType -> {
                    val targetLabel = ShaderLabelNode()
                    compiler.function.instructions.add(ShaderInsnNode(OP_F_CONVERT, type, targetLabel, input.label))
                    StackValue.Label(targetLabel)
                }
                getIntc(type.componentCount).classType -> {
                    val targetLabel = ShaderLabelNode()
                    compiler.function.instructions.add(ShaderInsnNode(OP_CONVERT_S_TO_F, type, targetLabel, input.label))
                    StackValue.Label(targetLabel)
                }
                else -> null
            }
        }
    }

    open class LongVector(
        mutableClassType: Type,
        immutableClassType: Type,
        mutable: Boolean,
        type: ShaderBytecodeType.Vector
    ) : JomlVectorTypeHandler(mutableClassType, immutableClassType, Type.LONG_TYPE, mutable, type) {
        override fun castConstructorType(
            compiler: JavaShaderMethodCompiler,
            arg: Type,
            input: StackValue,
            type: ShaderBytecodeType.Vector
        ): StackValue? {
            return when (arg) {
                getLongc(type.componentCount).classType -> input
                getIntc(type.componentCount).classType -> {
                    val targetLabel = ShaderLabelNode()
                    compiler.function.instructions.add(ShaderInsnNode(OP_S_CONVERT, type, targetLabel, input.label))
                    StackValue.Label(targetLabel)
                }
                else -> null
            }
        }
    }

    open class IntVector(
        mutableClassType: Type,
        immutableClassType: Type,
        mutable: Boolean,
        type: ShaderBytecodeType.Vector
    ) : JomlVectorTypeHandler(mutableClassType, immutableClassType, Type.INT_TYPE, mutable, type) {
        override fun castConstructorType(
            compiler: JavaShaderMethodCompiler,
            arg: Type,
            input: StackValue,
            type: ShaderBytecodeType.Vector
        ): StackValue? {
            return when (arg) {
                getIntc(type.componentCount).classType -> input
                else -> null
            }
        }
    }
}