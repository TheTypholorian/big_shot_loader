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
                val v = value.first() as Number

                when (to) {
                    ShaderBytecodeType.Integer.BYTE -> ShaderConstant(to, v.toByte())
                    ShaderBytecodeType.Integer.SHORT -> ShaderConstant(to, v.toShort())
                    ShaderBytecodeType.Integer.JAVA -> ShaderConstant(to, v.toInt())
                    ShaderBytecodeType.Integer.LONG -> ShaderConstant(to, v.toLong())
                    else -> null
                }
            }
            is ShaderBytecodeType.Float -> {
                val v = value.first() as Number

                when (to) {
                    ShaderBytecodeType.Float.JAVA -> ShaderConstant(to, v.toFloat())
                    ShaderBytecodeType.Float.DOUBLE -> ShaderConstant(to, v.toDouble())
                    else -> null
                }
            }
            else -> null
        }
    }

    override fun toString(): String {
        return "$value($type)"
    }
}
