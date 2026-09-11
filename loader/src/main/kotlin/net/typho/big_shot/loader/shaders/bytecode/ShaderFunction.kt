package net.typho.big_shot.loader.shaders.bytecode

data class ShaderFunction @JvmOverloads constructor(
    @JvmField
    val type: ShaderBytecodeType.Function,
    @JvmField
    val label: ShaderLabelNode = ShaderLabelNode()
) {
    @JvmField
    var controlMask = 0
    @JvmField
    val instructions = mutableListOf<ShaderInsnNode>()

    fun call(result: ShaderLabelNode, vararg args: ShaderLabelNode) = ShaderInsnNode(OP_FUNCTION_CALL, type.returnType, result, label, *args)
}