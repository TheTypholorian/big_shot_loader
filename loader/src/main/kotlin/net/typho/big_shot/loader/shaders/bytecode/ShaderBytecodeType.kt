package net.typho.big_shot.loader.shaders.bytecode

sealed interface ShaderBytecodeType {
    fun createLabelNode(): ShaderLabelNode = ShaderLabelNode(toString())

    fun createInsn(result: ShaderLabelNode): ShaderInsnNode

    object Void : ShaderBytecodeType {
        override fun createInsn(result: ShaderLabelNode): ShaderInsnNode {
            return ShaderInsnNode(OP_TYPE_VOID, result)
        }

        override fun toString(): String {
            return "Void"
        }
    }

    object Bool : ShaderBytecodeType {
        override fun createInsn(result: ShaderLabelNode): ShaderInsnNode {
            return ShaderInsnNode(OP_TYPE_BOOL, result)
        }

        override fun toString(): String {
            return "Bool"
        }
    }

    object Integer : ShaderBytecodeType {
        override fun createInsn(result: ShaderLabelNode): ShaderInsnNode {
            return ShaderInsnNode(OP_TYPE_INT, result)
        }

        override fun toString(): String {
            return "Integer"
        }
    }

    object Float : ShaderBytecodeType {
        override fun createInsn(result: ShaderLabelNode): ShaderInsnNode {
            return ShaderInsnNode(OP_TYPE_FLOAT, result)
        }

        override fun toString(): String {
            return "Float"
        }
    }

    data class Vector(
        @JvmField
        val componentType: ShaderBytecodeType,
        @JvmField
        val componentCount: Int
    ) : ShaderBytecodeType {
        override fun createInsn(result: ShaderLabelNode): ShaderInsnNode {
            return ShaderInsnNode(OP_TYPE_VECTOR, result, componentType, componentCount)
        }

        override fun toString(): String {
            return "Vec$componentCount$componentType"
        }
    }

    data class Matrix(
        @JvmField
        val columnType: ShaderBytecodeType,
        @JvmField
        val columnCount: Int
    ) : ShaderBytecodeType {
        override fun createInsn(result: ShaderLabelNode): ShaderInsnNode {
            return ShaderInsnNode(OP_TYPE_MATRIX, result, columnType, columnCount)
        }

        override fun toString(): String {
            return "Mat$columnCount$columnType"
        }
    }

    data class Function(
        @JvmField
        val returnType: ShaderBytecodeType,
        @JvmField
        val parameterTypes: List<ShaderBytecodeType>
    ) : ShaderBytecodeType {
        override fun createInsn(result: ShaderLabelNode): ShaderInsnNode {
            return ShaderInsnNode(OP_TYPE_FUNCTION, result, returnType, parameterTypes)
        }

        override fun toString(): String {
            return "$returnType(${parameterTypes.joinToString()})"
        }
    }

    // TODO image, sampler, sampled image, array, runtime array, struct, opaque, pointer
}