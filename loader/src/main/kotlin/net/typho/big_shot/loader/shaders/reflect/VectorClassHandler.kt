package net.typho.big_shot.loader.shaders.reflect

import net.typho.big_shot.loader.shaders.bytecode.OP_ACCESS_CHAIN
import net.typho.big_shot.loader.shaders.bytecode.OP_COMPOSITE_CONSTRUCT
import net.typho.big_shot.loader.shaders.bytecode.OP_CONVERT_S_TO_F
import net.typho.big_shot.loader.shaders.bytecode.OP_F_CONVERT
import net.typho.big_shot.loader.shaders.bytecode.OP_LOAD
import net.typho.big_shot.loader.shaders.bytecode.OP_STORE
import net.typho.big_shot.loader.shaders.bytecode.OP_VECTOR_SHUFFLE
import net.typho.big_shot.loader.shaders.bytecode.ShaderBytecodeType
import net.typho.big_shot.loader.shaders.bytecode.ShaderConstant
import net.typho.big_shot.loader.shaders.bytecode.ShaderInsnNode
import net.typho.big_shot.loader.shaders.bytecode.ShaderLabelNode
import net.typho.big_shot.loader.shaders.reflect.JavaShaderMethodCompiler.StackValue
import org.objectweb.asm.Type
import org.objectweb.asm.tree.MethodInsnNode
import java.util.function.Function

open class VectorClassHandler(
    @JvmField
    val mutableClassType: Type,
    @JvmField
    val immutableClassType: Type,
    @JvmField
    val componentType: Type,
    @JvmField
    val zero: ShaderConstant,
    @JvmField
    val one: ShaderConstant,
    @JvmField
    val mutable: Boolean,
    @JvmField
    val type: ShaderBytecodeType.Vector,
    @JvmField
    vararg val extraConstructorTypes: Pair<VectorClassHandler, Int>
) : JavaShaderClassHandler {
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

    companion object : Function<String, VectorClassHandler?> {
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

        override fun apply(className: String): VectorClassHandler? {
            return when (className) {
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

        fun JavaShaderMethodCompiler.createVector(type: ShaderBytecodeType.Vector, vararg values: StackValue.Labeled): ShaderLabelNode {
            return if (values.all { it is StackValue.Constant }) {
                parent.builder.getConstant(ShaderConstant(type, values.map { it.label }))
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

        fun JavaShaderMethodCompiler.vectorOpSelf(opcode: Int, type: ShaderBytecodeType.Vector, add: ShaderLabelNode, self: StackValue.Labeled) {
            vectorOp(opcode, type, self, add, self.label)
        }

        fun JavaShaderMethodCompiler.vectorInit(type: ShaderBytecodeType.Vector, vararg values: StackValue.Labeled) {
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

    override fun handleMethodCall(
        compiler: JavaShaderMethodCompiler,
        call: MethodInsnNode
    ) {
        fun error() {
            throw JavaShaderCompilationException("Unsupported method ${call.owner}.${call.name}${call.desc}")
        }

        when (call.name) {
            "<init>" -> {
                when (call.desc) {
                    "()V" -> compiler.vectorInit(type, StackValue.Label(compiler.parent.builder.getConstant(zero)))
                    voidSinglePrimDesc -> compiler.vectorInit(type, *compiler.stack.popSingleVectorComponent(type))
                    voidPrimDesc -> compiler.vectorInit(type, *compiler.stack.popVectorComponents(type))
                    voidImmutableDesc -> compiler.vectorInit(type, compiler.stack.pop() as StackValue.Labeled)
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
                        if (!handleExtraConstructor(compiler, call)) {
                            error()
                        }
                    }
                }
            }
        }

        error()
    }

    protected open fun handleExtraConstructor(compiler: JavaShaderMethodCompiler, call: MethodInsnNode): Boolean {
        return false
    }

    open class DoubleVector(
        mutableClassType: Type,
        immutableClassType: Type,
        mutable: Boolean,
        type: ShaderBytecodeType.Vector
    ) : VectorClassHandler(mutableClassType, immutableClassType, Type.DOUBLE_TYPE, ShaderConstant(ShaderBytecodeType.DOUBLE, 0.0), ShaderConstant(ShaderBytecodeType.DOUBLE, 1.0), mutable, type) {
    }

    open class FloatVector(
        mutableClassType: Type,
        immutableClassType: Type,
        mutable: Boolean,
        type: ShaderBytecodeType.Vector
    ) : VectorClassHandler(mutableClassType, immutableClassType, Type.FLOAT_TYPE, ShaderConstant(ShaderBytecodeType.FLOAT, 0f), ShaderConstant(ShaderBytecodeType.FLOAT, 1f), mutable, type) {
        override fun handleExtraConstructor(compiler: JavaShaderMethodCompiler, call: MethodInsnNode): Boolean {
            val args = Type.getArgumentTypes(call.desc)

            when (args.size) {
                1 -> {
                    if (type.componentCount == 2) {
                        when (args[0]) {
                            Double3c.classType, Float3c.classType, Int3c.classType -> {
                                val targetLabel = ShaderLabelNode()
                                compiler.function.instructions.add(ShaderInsnNode(OP_VECTOR_SHUFFLE, type, targetLabel, (compiler.stack.pop() as StackValue.Labeled).label, 0, 1))
                                compiler.vectorInit(type, StackValue.Label(targetLabel))
                                return true
                            }
                        }
                    }

                    val opcode = when (args[0]) {
                        getDoublec(type.componentCount).classType -> OP_F_CONVERT
                        getIntc(type.componentCount).classType -> OP_CONVERT_S_TO_F
                        else -> return false
                    }
                    val targetLabel = ShaderLabelNode()
                    compiler.function.instructions.add(ShaderInsnNode(opcode, type, targetLabel, (compiler.stack.pop() as StackValue.Labeled).label))
                    compiler.vectorInit(type, StackValue.Label(targetLabel))
                    return true
                }
                2 -> {
                    if (args[1] == componentType && type.componentCount > 2) {
                        val z = compiler.stack.pop() as StackValue.Labeled
                        val xy = compiler.stack.pop() as StackValue.Labeled

                        val first = when (args[0]) {
                            getFloatc(type.componentCount - 1).classType, immutableClassType -> xy
                            getDoublec(type.componentCount - 1).classType -> {
                                val targetLabel = ShaderLabelNode()
                                compiler.function.instructions.add(ShaderInsnNode(OP_F_CONVERT, type, targetLabel, xy.label))
                                StackValue.Label(targetLabel)
                            }
                            getIntc(type.componentCount - 1).classType -> {
                                val targetLabel = ShaderLabelNode()
                                compiler.function.instructions.add(ShaderInsnNode(OP_CONVERT_S_TO_F, type, targetLabel, xy.label))
                                StackValue.Label(targetLabel)
                            }
                            else -> return false
                        }
                        compiler.vectorInit(type, first, z)
                        return true
                    }
                }
                3 -> {
                    if (args[1] == componentType && args[2] == componentType && type.componentCount > 3) {
                        val z = compiler.stack.pop() as StackValue.Labeled
                        val xy = compiler.stack.pop() as StackValue.Labeled

                        val first = when (args[0]) {
                            getFloatc(type.componentCount - 2).classType, immutableClassType -> xy
                            getDoublec(type.componentCount - 2).classType -> {
                                val targetLabel = ShaderLabelNode()
                                compiler.function.instructions.add(ShaderInsnNode(OP_F_CONVERT, type, targetLabel, xy.label))
                                StackValue.Label(targetLabel)
                            }
                            getIntc(type.componentCount - 2).classType -> {
                                val targetLabel = ShaderLabelNode()
                                compiler.function.instructions.add(ShaderInsnNode(OP_CONVERT_S_TO_F, type, targetLabel, xy.label))
                                StackValue.Label(targetLabel)
                            }
                            else -> return false
                        }
                        compiler.vectorInit(type, first, z)
                        return true
                    }
                }
            }

            return false
        }
    }

    open class LongVector(
        mutableClassType: Type,
        immutableClassType: Type,
        mutable: Boolean,
        type: ShaderBytecodeType.Vector
    ) : VectorClassHandler(mutableClassType, immutableClassType, Type.LONG_TYPE, ShaderConstant(ShaderBytecodeType.LONG, 0L), ShaderConstant(ShaderBytecodeType.LONG, 1L), mutable, type) {
    }

    open class IntVector(
        mutableClassType: Type,
        immutableClassType: Type,
        mutable: Boolean,
        type: ShaderBytecodeType.Vector
    ) : VectorClassHandler(mutableClassType, immutableClassType, Type.INT_TYPE, ShaderConstant(ShaderBytecodeType.INT, 0), ShaderConstant(ShaderBytecodeType.INT, 1), mutable, type) {
    }

    /*

                        val args = Type.getArgumentTypes(call.desc)

                        if (args.size == 1) {
                            val arg = args[0]

                            if (arg.sort == Type.OBJECT) {
                                for ((type, opcode) in extraConstructorTypes) {
                                    if (type.classType == arg) {
                                        val targetLabel = ShaderLabelNode()
                                        compiler.function.instructions.add(ShaderInsnNode(opcode, type.type, targetLabel, (compiler.stack.pop() as StackValue.Labeled).label))
                                        compiler.vectorInit(type.type, StackValue.Label(targetLabel))
                                        break
                                    }
                                }
                            }
                        }
     */
}