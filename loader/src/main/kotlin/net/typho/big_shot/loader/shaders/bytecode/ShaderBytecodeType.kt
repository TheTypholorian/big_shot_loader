package net.typho.big_shot.loader.shaders.bytecode

import org.objectweb.asm.Type

sealed interface ShaderBytecodeType {
    companion object {
        @JvmField
        val BYTE = Integer(8, true)
        @JvmField
        val SHORT = Integer(16, true)
        @JvmField
        val INT = Integer(32, true)
        @JvmField
        val LONG = Integer(64, true)
        @JvmField
        val FLOAT = Float(32)
        @JvmField
        val DOUBLE = Float(64)

        @JvmField
        val VECTOR2D = Vector(DOUBLE, 2)
        @JvmField
        val VECTOR2F = Vector(FLOAT, 2)
        @JvmField
        val VECTOR2I = Vector(INT, 2)
        @JvmField
        val VECTOR2L = Vector(LONG, 2)
        @JvmField
        val VECTOR3D = Vector(DOUBLE, 3)
        @JvmField
        val VECTOR3F = Vector(FLOAT, 3)
        @JvmField
        val VECTOR3I = Vector(INT, 3)
        @JvmField
        val VECTOR3L = Vector(LONG, 3)
        @JvmField
        val VECTOR4D = Vector(DOUBLE, 4)
        @JvmField
        val VECTOR4F = Vector(FLOAT, 4)
        @JvmField
        val VECTOR4I = Vector(INT, 4)
        @JvmField
        val VECTOR4L = Vector(LONG, 4)

        @JvmStatic
        fun convertJavaType(type: Type): ShaderBytecodeType {
            return when (type.sort) {
                Type.VOID -> Void
                Type.BOOLEAN -> Bool

                Type.BYTE -> BYTE
                Type.SHORT -> SHORT
                Type.INT -> INT
                Type.LONG -> LONG

                Type.FLOAT -> FLOAT
                Type.DOUBLE -> DOUBLE

                Type.OBJECT -> when (type.internalName) {
                    "java/lang/Void", "kotlin/Unit" -> Void
                    "java/lang/Boolean" -> Bool

                    "java/lang/Byte" -> BYTE
                    "java/lang/Short" -> SHORT
                    "java/lang/Integer" -> INT
                    "java/lang/Long" -> LONG

                    "java/lang/Float" -> FLOAT
                    "java/lang/Double" -> DOUBLE

                    "org/joml/Vector2dc", "org/joml/Vector2d" -> VECTOR2D
                    "org/joml/Vector2fc", "org/joml/Vector2f" -> VECTOR2F
                    "org/joml/Vector2ic", "org/joml/Vector2i" -> VECTOR2I
                    "org/joml/Vector2Lc", "org/joml/Vector2L" -> VECTOR2L

                    "org/joml/Vector3dc", "org/joml/Vector3d" -> VECTOR3D
                    "org/joml/Vector3fc", "org/joml/Vector3f" -> VECTOR3F
                    "org/joml/Vector3ic", "org/joml/Vector3i" -> VECTOR3I
                    "org/joml/Vector3Lc", "org/joml/Vector3L" -> VECTOR3L

                    "org/joml/Vector4dc", "org/joml/Vector4d" -> VECTOR4D
                    "org/joml/Vector4fc", "org/joml/Vector4f" -> VECTOR4F
                    "org/joml/Vector4ic", "org/joml/Vector4i" -> VECTOR4I
                    "org/joml/Vector4Lc", "org/joml/Vector4L" -> VECTOR4L
                    else -> throw IllegalArgumentException("Cannot convert type $type to spir-v")
                }

                Type.ARRAY -> Array(convertJavaType(type.elementType), null)
                Type.METHOD -> Function(convertJavaType(type.returnType), type.argumentTypes.map { convertJavaType(it) })
                // TODO other types

                else -> throw IllegalArgumentException("Cannot convert type $type to spir-v")
            }
        }
    }

    val rootType: ShaderBytecodeType
        get() = this

    fun createLabelNode(): ShaderLabelNode = ShaderLabelNode(toString())

    fun createInsn(result: ShaderLabelNode, builder: ShaderBytecodeBuilder): ShaderInsnNode

    fun getLabel(builder: ShaderBytecodeBuilder): ShaderLabelNode {
        return builder.types.computeIfAbsent(this) { createLabelNode() }
    }

    object Void : ShaderBytecodeType {
        override fun createInsn(result: ShaderLabelNode, builder: ShaderBytecodeBuilder): ShaderInsnNode {
            return ShaderInsnNode(OP_TYPE_VOID, result)
        }

        override fun toString(): String {
            return "void"
        }
    }

    object Bool : ShaderBytecodeType {
        override fun createInsn(result: ShaderLabelNode, builder: ShaderBytecodeBuilder): ShaderInsnNode {
            return ShaderInsnNode(OP_TYPE_BOOL, result)
        }

        override fun toString(): String {
            return "boolean"
        }
    }

    data class Integer(
        @JvmField
        val width: Int,
        @JvmField
        val signed: Boolean
    ) : ShaderBytecodeType {
        override fun createInsn(result: ShaderLabelNode, builder: ShaderBytecodeBuilder): ShaderInsnNode {
            return ShaderInsnNode(OP_TYPE_INT, result, width, if (signed) 1 else 0)
        }

        override fun toString(): String {
            return buildString {
                if (!signed) {
                    append('s')
                }

                append("int")
                append(width)
            }
        }
    }

    data class Float(
        @JvmField
        val width: Int
    ) : ShaderBytecodeType {
        override fun createInsn(result: ShaderLabelNode, builder: ShaderBytecodeBuilder): ShaderInsnNode {
            return ShaderInsnNode(OP_TYPE_FLOAT, result, width)
        }

        override fun toString(): String {
            return "float$width"
        }
    }

    data class Vector(
        @JvmField
        val componentType: ShaderBytecodeType,
        @JvmField
        val componentCount: Int
    ) : ShaderBytecodeType {
        override fun createInsn(result: ShaderLabelNode, builder: ShaderBytecodeBuilder): ShaderInsnNode {
            return ShaderInsnNode(OP_TYPE_VECTOR, result, componentType, componentCount)
        }

        override fun toString(): String {
            return "Vec$componentCount$componentType"
        }

        override fun getLabel(builder: ShaderBytecodeBuilder): ShaderLabelNode {
            componentType.getLabel(builder)
            return super.getLabel(builder)
        }
    }

    data class Matrix(
        @JvmField
        val columnType: ShaderBytecodeType,
        @JvmField
        val columnCount: Int
    ) : ShaderBytecodeType {
        override fun createInsn(result: ShaderLabelNode, builder: ShaderBytecodeBuilder): ShaderInsnNode {
            return ShaderInsnNode(OP_TYPE_MATRIX, result, columnType, columnCount)
        }

        override fun toString(): String {
            return "Mat$columnCount$columnType"
        }

        override fun getLabel(builder: ShaderBytecodeBuilder): ShaderLabelNode {
            columnType.getLabel(builder)
            return super.getLabel(builder)
        }
    }

    data class Function(
        @JvmField
        val returnType: ShaderBytecodeType,
        @JvmField
        val parameterTypes: List<ShaderBytecodeType>
    ) : ShaderBytecodeType {
        override fun createInsn(result: ShaderLabelNode, builder: ShaderBytecodeBuilder): ShaderInsnNode {
            return ShaderInsnNode(OP_TYPE_FUNCTION, result, returnType, parameterTypes)
        }

        override fun toString(): String {
            return "$returnType(${parameterTypes.joinToString()})"
        }

        override fun getLabel(builder: ShaderBytecodeBuilder): ShaderLabelNode {
            returnType.getLabel(builder)
            parameterTypes.forEach { it.getLabel(builder) }
            return super.getLabel(builder)
        }
    }

    data class Array(
        @JvmField
        val elementType: ShaderBytecodeType,
        @JvmField
        val length: Int?
    ) : ShaderBytecodeType {
        override val rootType: ShaderBytecodeType
            get() = elementType.rootType

        override fun createInsn(result: ShaderLabelNode, builder: ShaderBytecodeBuilder): ShaderInsnNode {
            return length?.let { ShaderInsnNode(OP_TYPE_ARRAY, result, elementType, builder.getConstant(ShaderConstant(INT, it))) } ?: ShaderInsnNode(OP_TYPE_RUNTIME_ARRAY, result, elementType)
        }

        override fun toString(): String {
            return length?.let { "$elementType[$it]" } ?: "$elementType[]"
        }

        override fun getLabel(builder: ShaderBytecodeBuilder): ShaderLabelNode {
            elementType.getLabel(builder)
            return super.getLabel(builder)
        }
    }

    data class Pointer(
        @JvmField
        val storageClass: Int,
        @JvmField
        val type: ShaderBytecodeType
    ) : ShaderBytecodeType {
        override fun createInsn(result: ShaderLabelNode, builder: ShaderBytecodeBuilder): ShaderInsnNode {
            return ShaderInsnNode(OP_TYPE_POINTER, result, storageClass, type)
        }

        override fun toString(): String {
            return "Pointer$type"
        }

        override fun getLabel(builder: ShaderBytecodeBuilder): ShaderLabelNode {
            type.getLabel(builder)
            return super.getLabel(builder)
        }
    }

    // TODO image, sampler, sampled image, struct, opaque
}