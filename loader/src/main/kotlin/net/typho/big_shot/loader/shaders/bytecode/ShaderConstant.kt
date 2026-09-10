package net.typho.big_shot.loader.shaders.bytecode

data class ShaderConstant(
    @JvmField
    val type: ShaderBytecodeType,
    @JvmField
    val value: List<Any>
) {
    fun createLabelNode(): ShaderLabelNode = ShaderLabelNode(toString())

    fun createInsn(result: ShaderLabelNode) = if (value.size == 1) ShaderInsnNode(OP_CONSTANT, type, result, value) else ShaderInsnNode(OP_CONSTANT_COMPOSITE, type, result, value)

    fun tryCast(to: ShaderBytecodeType): ShaderConstant? {
        return when (to) {
            ShaderBytecodeType.BYTE -> ShaderConstant(to, listOf((value.first() as Number).toByte()))
            ShaderBytecodeType.SHORT -> ShaderConstant(to, listOf((value.first() as Number).toShort()))
            ShaderBytecodeType.INT -> ShaderConstant(to, listOf((value.first() as Number).toInt()))
            ShaderBytecodeType.LONG -> ShaderConstant(to, listOf((value.first() as Number).toLong()))
            ShaderBytecodeType.FLOAT -> ShaderConstant(to, listOf((value.first() as Number).toFloat()))
            ShaderBytecodeType.DOUBLE -> ShaderConstant(to, listOf((value.first() as Number).toDouble()))
            else -> null
        }
    }

    override fun toString(): String {
        return (if (value.size == 1) value.first() else value).toString()
    }
}
