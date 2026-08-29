package net.typho.big_shot.loader.shaders.bytecode

import net.typho.big_shot.loader.util.ExpandingByteBuffer
import net.typho.big_shot.loader.util.ExpandingByteBuffer.Companion.merge
import java.nio.ByteBuffer

class ShaderBytecodeBuilder(
    @JvmField
    val execModel: Int
) {
    @JvmField
    var labelCounter = 0
    internal val labels = mutableSetOf<ShaderLabelNode>()
    @JvmField
    val imports = linkedMapOf<String, ShaderLabelNode>()
    @JvmField
    val types = linkedMapOf<ShaderBytecodeType, ShaderLabelNode>()

    @JvmField
    val capabilities = mutableSetOf<Int>()
    @JvmField
    val functions = mutableListOf<ShaderFunction>()

    fun getType(type: ShaderBytecodeType) = types.computeIfAbsent(type) { it.createLabelNode() }

    fun import(name: String) = imports.computeIfAbsent(name) { ShaderLabelNode(name) }

    fun build(entrypoint: ShaderFunction): ByteBuffer {
        val header = ExpandingByteBuffer(256)
        header.expand(28)
            .putInt(SPIRV_MAGIC)
            .putInt(0x10600) // spir-v 1.6
            .putInt(0) // generator magic
            .putInt(0) // label count, put later
            .putInt(0) // reserved

        for (cap in capabilities) {
            ShaderInsnNode(OP_CAPABILITY, cap).flatten(this).get(header)
        }

        for ((import, label) in imports) {
            ShaderInsnNode(OP_EXT_INST_IMPORT, label, import).flatten(this).get(header)
        }

        ShaderInsnNode(OP_MEMORY_MODEL, ADDRESS_MODE_LOGICAL, MEMORY_MODEL_GLSL_450).flatten(this).get(header)
        ShaderInsnNode(OP_ENTRY_POINT, execModel, entrypoint, entrypoint.name ?: "main" /* TODO inputs and outputs */).flatten(this).get(header)

        val annotations = ExpandingByteBuffer(256)
        val types = ExpandingByteBuffer(512)
        val body = ExpandingByteBuffer(4096)

        for (func in functions) {
            ShaderInsnNode(OP_FUNCTION, func.type.returnType, func, func.controlMask, func.type).flatten(this).get(body)
            func.instructions.forEach { it.flatten(this).get(body) }
            ShaderInsnNode(OP_FUNCTION_END).get(body)
        }

        for ((type, label) in this.types) {
            type.createInsn(label).flatten(this).get(types)
        }

        labels.addAll(this.types.values)
        val labelNameInsns = labels.mapNotNull { it.getNameInsn(this)?.flatten(this) }
        val debugInfoSize = labelNameInsns.sumOf { it.words * 4 }

        val debug = ExpandingByteBuffer(debugInfoSize)

        for (label in labelNameInsns) {
            label.get(debug)
        }

        header.buffer.putInt(12, labelCounter)

        return listOf(header, debug, annotations, types, body).merge()
    }
}