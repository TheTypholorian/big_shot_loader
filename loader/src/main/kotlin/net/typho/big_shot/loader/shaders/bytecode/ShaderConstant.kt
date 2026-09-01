package net.typho.big_shot.loader.shaders.bytecode

data class ShaderConstant(
    @JvmField
    val type: ShaderBytecodeType,
    @JvmField
    val value: List<Any>
) {
    constructor(type: ShaderBytecodeType, vararg values: Any?) : this(type, values.filterNotNull())

    fun createLabelNode(): ShaderLabelNode = ShaderLabelNode(toString())

    fun createInsn(result: ShaderLabelNode) = if (value.size == 1) ShaderInsnNode(OP_CONSTANT, type, result, value) else ShaderInsnNode(OP_CONSTANT_COMPOSITE, type, result, value)

    fun tryCast(to: ShaderBytecodeType): ShaderConstant? {
        return when (to) {
            ShaderBytecodeType.BYTE -> ShaderConstant(to, (value.first() as Number).toByte())
            ShaderBytecodeType.SHORT -> ShaderConstant(to, (value.first() as Number).toShort())
            ShaderBytecodeType.INT -> ShaderConstant(to, (value.first() as Number).toInt())
            ShaderBytecodeType.LONG -> ShaderConstant(to, (value.first() as Number).toLong())
            ShaderBytecodeType.FLOAT -> ShaderConstant(to, (value.first() as Number).toFloat())
            ShaderBytecodeType.DOUBLE -> ShaderConstant(to, (value.first() as Number).toDouble())
            else -> null
        }
    }

    override fun toString(): String {
        return (if (value.size == 1) value.first() else value).toString()
    }
}
