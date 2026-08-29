package net.typho.big_shot.loader.util

import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.libc.LibCString
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ExpandingByteBuffer @JvmOverloads constructor(
    buffer: ByteBuffer,
    @JvmField
    val resizeFactor: Int = 2
) {
    var buffer: ByteBuffer = buffer
        private set

    @JvmOverloads
    constructor(initialCapacity: Int, resizeFactor: Int = 2) : this(ByteBuffer.allocateDirect(initialCapacity).order(ByteOrder.nativeOrder()).limit(0), resizeFactor)

    fun require(size: Int): ByteBuffer {
        if (buffer.capacity() >= size) {
            buffer.limit(buffer.limit().coerceAtLeast(size))
        } else {
            val newBuffer = ByteBuffer.allocateDirect((buffer.capacity() * resizeFactor).coerceAtLeast(size)).order(ByteOrder.nativeOrder()).limit(size)
            MemoryUtil.memCopy(newBuffer, buffer.duplicate().order(buffer.order()).clear().limit(buffer.limit()))
            buffer = newBuffer.position(buffer.position())
        }

        return buffer
    }

    fun expand(bytes: Int) = require(buffer.limit() + bytes)

    fun insert(index: Int, bytes: Int): ByteBuffer {
        if (index == buffer.limit()) {
            return expand(bytes).slice(index, bytes)
        }

        if (buffer.capacity() >= buffer.limit() + bytes) {
            buffer.limit(buffer.limit() + bytes)
            LibCString.memmove(buffer.duplicate().order(buffer.order()).position(index + bytes), buffer.duplicate().order(buffer.order()).position(index).limit(buffer.limit()))
        } else {
            val newBuffer = ByteBuffer.allocateDirect((buffer.capacity() * resizeFactor).coerceAtLeast(buffer.limit() + bytes)).order(ByteOrder.nativeOrder()).limit(buffer.limit() + bytes)
            LibCString.memmove(newBuffer, buffer.duplicate().order(buffer.order()).clear().limit(index))
            LibCString.memmove(newBuffer.duplicate().order(buffer.order()).position(index + bytes), buffer.duplicate().order(buffer.order()).position(index).limit(buffer.limit()))
            buffer = newBuffer.position(buffer.position())
        }

        return buffer.slice(index, bytes)
    }

    companion object {
        @JvmStatic
        fun List<ExpandingByteBuffer>.merge(): ByteBuffer {
            forEach { it.buffer.flip() }

            val buffer = ByteBuffer.allocateDirect(sumOf { it.buffer.limit() }).order(ByteOrder.nativeOrder())
            var index = 0

            forEach {
                MemoryUtil.memCopy(it.buffer, buffer.slice(index, it.buffer.limit()))
                index += it.buffer.limit()
            }

            return buffer
        }
    }
}