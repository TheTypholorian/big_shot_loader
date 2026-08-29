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

    override fun toString(): String {
        return "$value($type)"
    }
}
