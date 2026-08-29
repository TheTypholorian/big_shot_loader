package net.typho.big_shot.loader.shaders.bytecode

class ShaderFunction(
    @JvmField
    val type: ShaderBytecodeType.Function,
    name: String? = null
) : ShaderLabelNode(name) {
    @JvmField
    var controlMask = 0
    @JvmField
    val instructions = mutableListOf<ShaderInsnNode>()

    fun call(result: ShaderLabelNode, parameters: List<Any>) = ShaderInsnNode(OP_FUNCTION_CALL, type.returnType, result, this, parameters)
}