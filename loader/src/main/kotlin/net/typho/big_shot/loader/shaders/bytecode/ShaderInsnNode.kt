package net.typho.big_shot.loader.shaders.bytecode

import net.typho.big_shot.loader.util.ExpandingByteBuffer
import java.nio.ByteBuffer
import java.util.function.Supplier

data class ShaderInsnNode(
    @JvmField
    val opcode: Int,
    @JvmField
    val values: List<Any>
) {
    constructor(opcode: Int, vararg values: Any) : this(opcode, values.asList())

    val words by lazy {
        val words = values.sumOf { value ->
            when (value) {
                is Int, is ShaderLabelNode -> 1
                is Float -> 1
                is Double -> 2
                is CharSequence -> (value.length + 3) / 4 // round up to nearest word
                else -> throw IllegalArgumentException("Illegal ShaderInsnNode value $value (${value.javaClass})")
            }
        } + 1 // include the header word

        if (words > 0xFFFF) {
            throw IllegalArgumentException("Cannot have a ShaderInsnNode that is longer than ${0xFFFF} words")
        }

        words
    }

    @JvmOverloads
    fun flatten(builder: ShaderBytecodeBuilder? = null): ShaderInsnNode {
        return ShaderInsnNode(opcode, values.flatMap { it as? Iterable<*> ?: listOf(it) }.map { value ->
            when (value) {
                is Supplier<*> -> value.get()!!
                is Function0<*> -> value()!!
                is ShaderLabelNode -> value.getId(builder)
                is ShaderBytecodeType -> builder!!.getType(value).getId(builder)
                else -> value
            }!!
        })
    }

    fun get(buffer: ExpandingByteBuffer) {
        get(buffer.expand(words * 4))
    }

    fun get(buffer: ByteBuffer) {
        val values = values.map { value ->
            when (value) {
                is Supplier<*> -> value.get()!!
                is Function0<*> -> value()!!
                else -> value
            }
        }

        values.forEachIndexed { index, value ->
            if (value is CharSequence && index < values.size - 1) {
                throw IllegalArgumentException("ShaderInsnNode values of type CharSequence must be the last value in the list")
            }
        }

        val buffer = buffer.putInt((words shl 16) or opcode)

        for (value in values) {
            when (value) {
                is Int -> buffer.putInt(value)
                is Float -> buffer.putFloat(value)
                is Double -> buffer.putDouble(value)
                is CharSequence -> {
                    value.forEach {
                        if (it.code > 0xFF) {
                            throw IllegalArgumentException("CharSequence characters in ShaderInsnNode values must be in the range 0-255")
                        }

                        buffer.put(it.code.toByte())
                    }

                    buffer.position(buffer.position() + ((value.length + 3) / 4) * 4 - value.length) // align to word
                }
            }
        }
    }
}