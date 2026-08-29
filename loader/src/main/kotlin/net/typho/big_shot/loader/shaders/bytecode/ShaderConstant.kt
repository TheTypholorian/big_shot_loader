package net.typho.big_shot.loader.shaders.bytecode

data class ShaderConstant(
    @JvmField
    val type: ShaderBytecodeType,
    @JvmField
    val value: List<Any>
) {
    constructor(type: ShaderBytecodeType, vararg values: Any?) : this(type, values.filterNotNull())

    fun createLabelNode(): ShaderLabelNode = ShaderLabelNode(toString())

    fun createInsn(result: ShaderLabelNode) = ShaderInsnNode(OP_CONSTANT, type, result, value)

    fun cast(to: ShaderBytecodeType): ShaderConstant? {
        return when (to) {
            is ShaderBytecodeType.Integer -> {
                when (to) {
                    ShaderBytecodeType.Integer.BYTE -> ShaderConstant(to, (value.first() as Number).toByte())
                    ShaderBytecodeType.Integer.SHORT -> ShaderConstant(to, (value.first() as Number).toShort())
                    ShaderBytecodeType.Integer.JAVA -> ShaderConstant(to, (value.first() as Number).toInt())
                    ShaderBytecodeType.Integer.LONG -> ShaderConstant(to, (value.first() as Number).toLong())
                    else -> null
                }
            }
            is ShaderBytecodeType.Float -> {
                when (to) {
                    ShaderBytecodeType.Float.JAVA -> ShaderConstant(to, (value.first() as Number).toFloat())
                    ShaderBytecodeType.Float.DOUBLE -> ShaderConstant(to, (value.first() as Number).toDouble())
                    else -> null
                }
            }
            else -> null
        }
    }

    override fun toString(): String {
        return if (value.size == 1) "${value.first()}($type)" else "$value($type)"
    }
}
