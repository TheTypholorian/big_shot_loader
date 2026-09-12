package net.typho.big_shot.loader.shaders.bytecode

import net.typho.big_shot.loader.util.ExpandingByteBuffer

data class ShaderFunction<I : ShaderFunction.Instruction> @JvmOverloads constructor(
    @JvmField
    val type: ShaderBytecodeType.Function,
    @JvmField
    val label: ShaderLabelNode = ShaderLabelNode()
) {
    @JvmField
    var controlMask = 0
    @JvmField
    val instructions = mutableListOf<I>()

    fun call(result: ShaderLabelNode, vararg args: ShaderLabelNode) = ShaderInsnNode(OP_FUNCTION_CALL, type.returnType, result, label, *args)

    interface Instruction {
        fun write(builder: ShaderBytecodeBuilder, buffer: ExpandingByteBuffer)
    }
}