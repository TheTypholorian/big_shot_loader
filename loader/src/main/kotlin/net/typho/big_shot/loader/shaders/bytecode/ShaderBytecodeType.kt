package net.typho.big_shot.loader.shaders.bytecode

sealed interface ShaderBytecodeType {
    val rootType: ShaderBytecodeType
        get() = this

    fun createLabelNode(): ShaderLabelNode = ShaderLabelNode(toString())

    fun createInsn(result: ShaderLabelNode, builder: ShaderBytecodeBuilder): ShaderInsnNode

    fun register(builder: ShaderBytecodeBuilder) {
        builder.types.computeIfAbsent(this) { createLabelNode() }
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
        companion object {
            @JvmField
            val BYTE = Integer(8, true)
            @JvmField
            val SHORT = Integer(16, true)
            @JvmField
            val JAVA = Integer(32, true)
            @JvmField
            val LONG = Integer(64, true)
        }

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
        companion object {
            @JvmField
            val JAVA = Float(32)
            @JvmField
            val DOUBLE = Float(64)
        }

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

        override fun register(builder: ShaderBytecodeBuilder) {
            super.register(builder)
            componentType.register(builder)
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

        override fun register(builder: ShaderBytecodeBuilder) {
            super.register(builder)
            columnType.register(builder)
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

        override fun register(builder: ShaderBytecodeBuilder) {
            super.register(builder)
            returnType.register(builder)
            parameterTypes.forEach { it.register(builder) }
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
            return length?.let { ShaderInsnNode(OP_TYPE_ARRAY, result, elementType, builder.getConstant(ShaderConstant(Integer.JAVA, it))) } ?: ShaderInsnNode(OP_TYPE_RUNTIME_ARRAY, result, elementType)
        }

        override fun toString(): String {
            return length?.let { "$elementType[$it]" } ?: "$elementType[]"
        }

        override fun register(builder: ShaderBytecodeBuilder) {
            super.register(builder)
            elementType.register(builder)
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
            return "Pointer$storageClass$type"
        }

        override fun register(builder: ShaderBytecodeBuilder) {
            super.register(builder)
            type.register(builder)
        }
    }

    // TODO image, sampler, sampled image, struct, opaque
}