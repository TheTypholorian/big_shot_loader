package net.typho.big_shot.loader.shaders.bytecode

import org.objectweb.asm.Type

data class ShaderVariable @JvmOverloads constructor(
    @JvmField
    val type: ShaderBytecodeType.Pointer,
    @JvmField
    val label: ShaderLabelNode = ShaderLabelNode(),
    @JvmField
    val javaType: Type? = null,
    @JvmField
    val initializer: ShaderLabelNode? = null,
    @JvmField
    val location: Int? = null
)