package net.typho.big_shot.merger

import net.typho.big_shot.data.env.EnvironmentRestriction
import net.typho.big_shot.merger.AnnotationInfo.Companion.toInfo
import net.typho.data_util.SingleValueInput.Companion.readVarInt
import net.typho.data_util.SingleValueOutput.Companion.writeVarInt
import org.objectweb.asm.Type
import org.objectweb.asm.TypePath
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.InnerClassNode
import org.objectweb.asm.tree.TypeAnnotationNode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInput
import java.io.DataInputStream
import java.io.DataOutput
import java.io.DataOutputStream

@Retention(AnnotationRetention.BINARY)
internal annotation class InnerClassInfo(
    val name: String,
    val outerName: String,
    val innerName: String,
    val access: Int
) {
    companion object {
        @JvmStatic
        fun InnerClassInfo.toNode() = InnerClassNode(name, outerName.ifEmpty { null }, innerName.ifEmpty { null }, access)

        @JvmStatic
        fun InnerClassNode.toInfo() = InnerClassInfo(name, outerName ?: "", innerName ?: "", access)
    }
}

@Retention(AnnotationRetention.BINARY)
internal annotation class TypeAnnotationInfo(
    val typeRef: Int,
    val typePath: String,
    val anno: AnnotationInfo
) {
    companion object {
        @JvmStatic
        fun TypeAnnotationInfo.toNode() = TypeAnnotationNode(typeRef, TypePath.fromString(typePath), anno.desc).also { AnnotationInfo.toNode(it, anno.data) }

        @JvmStatic
        fun TypeAnnotationNode.toTypeInfo() = TypeAnnotationInfo(typeRef, typePath.toString(), toInfo())
    }
}

@Retention(AnnotationRetention.BINARY)
internal annotation class AnnotationInfo(
    val desc: String,
    val data: ByteArray
) {
    companion object {
        const val NULL = 0.toByte()
        const val BYTE = 1.toByte()
        const val SHORT = 2.toByte()
        const val CHAR = 3.toByte()
        const val INT = 4.toByte()
        const val LONG = 5.toByte()
        const val FLOAT = 6.toByte()
        const val DOUBLE = 7.toByte()
        const val STRING = 8.toByte()
        const val TYPE = 9.toByte()
        const val LIST = 10.toByte()
        const val ARRAY = 11.toByte()
        const val ANNO = 12.toByte()

        internal fun readValue(input: DataInput): Any? = when (input.readByte()) {
            NULL -> null
            BYTE -> input.readByte()
            SHORT -> input.readShort()
            CHAR -> input.readChar()
            INT -> input.readInt()
            LONG -> input.readLong()
            FLOAT -> input.readFloat()
            DOUBLE -> input.readDouble()
            STRING -> input.readUTF()
            TYPE -> Type.getType(input.readUTF())
            LIST -> List(input.readVarInt()) { readValue(input) }
            ARRAY -> Array(input.readVarInt()) { readValue(input) }
            ANNO -> AnnotationNode(input.readUTF()).also { toNode(it, ByteArray(input.readVarInt()).also { input.readFully(it) }) }
            else -> throw AssertionError()
        }

        internal fun toNode(node: AnnotationNode, bytes: ByteArray) {
            val stream = DataInputStream(ByteArrayInputStream(bytes))
            node.values = mutableListOf()

            while (stream.available() > 0) {
                node.values.add(stream.readUTF())
                node.values.add(readValue(stream))
            }
        }

        internal fun writeValue(output: DataOutput, value: Any?) {
            when (value) {
                null -> output.writeByte(NULL.toInt())
                is Byte -> {
                    output.writeByte(BYTE.toInt())
                    output.writeByte(value.toInt())
                }
                is Short -> {
                    output.writeByte(SHORT.toInt())
                    output.writeShort(value.toInt())
                }
                is Int -> {
                    output.writeByte(INT.toInt())
                    output.writeInt(value)
                }
                is Long -> {
                    output.writeByte(LONG.toInt())
                    output.writeLong(value)
                }
                is Float -> {
                    output.writeByte(FLOAT.toInt())
                    output.writeFloat(value)
                }
                is Double -> {
                    output.writeByte(DOUBLE.toInt())
                    output.writeDouble(value)
                }
                is String -> {
                    output.writeByte(STRING.toInt())
                    output.writeUTF(value)
                }
                is Type -> {
                    output.writeByte(TYPE.toInt())
                    output.writeUTF(value.internalName)
                }
                is List<*> -> {
                    output.writeByte(LIST.toInt())
                    output.writeVarInt(value.size)
                    value.forEach { writeValue(output, it) }
                }
                is Array<*> -> {
                    output.writeByte(ARRAY.toInt())
                    output.writeVarInt(value.size)
                    value.forEach { writeValue(output, it) }
                }
                is AnnotationNode -> {
                    output.writeByte(ANNO.toInt())
                    output.write(fromNode(value))
                }
                else -> throw IllegalArgumentException()
            }
        }

        internal fun fromNode(node: AnnotationNode): ByteArray {
            val iterator = (node.values ?: return byteArrayOf()).iterator()
            val bytes = ByteArrayOutputStream()
            val output = DataOutputStream(bytes)

            while (iterator.hasNext()) {
                output.writeUTF(iterator.next() as String)
                writeValue(output, iterator.next())
            }

            return bytes.toByteArray()
        }

        @JvmStatic
        fun AnnotationInfo.toNode() = AnnotationNode(desc).also { toNode(it, data) }

        @JvmStatic
        fun AnnotationNode.toInfo() = AnnotationInfo(desc, fromNode(this))
    }
}

@Retention(AnnotationRetention.BINARY)
internal annotation class IntValue(
    val value: Int,
    val `for`: EnvironmentRestriction
)

@Retention(AnnotationRetention.BINARY)
internal annotation class StringValue(
    val value: String,
    val `for`: EnvironmentRestriction
)

@Retention(AnnotationRetention.BINARY)
internal annotation class StringArrayValue(
    val value: Array<String>,
    val `for`: EnvironmentRestriction
)

@Retention(AnnotationRetention.BINARY)
internal annotation class AnnotationArrayValue(
    val value: Array<AnnotationInfo>,
    val `for`: EnvironmentRestriction
)

@Retention(AnnotationRetention.BINARY)
internal annotation class InnerClassArrayValue(
    val value: Array<InnerClassInfo>,
    val `for`: EnvironmentRestriction
)

@Repeatable
@Retention(AnnotationRetention.BINARY)
internal annotation class MergeIntInfo(
    val id: Byte,
    val value: Array<IntValue>
)

@Repeatable
@Retention(AnnotationRetention.BINARY)
internal annotation class MergeStringInfo(
    val id: Byte,
    val value: Array<StringValue>
)

@Repeatable
@Retention(AnnotationRetention.BINARY)
internal annotation class MergeStringArrayInfo(
    val id: Byte,
    val value: Array<StringArrayValue>
)

@Repeatable
@Retention(AnnotationRetention.BINARY)
internal annotation class MergeAnnotationArrayInfo(
    val id: Byte,
    val value: Array<AnnotationArrayValue>
)

@Repeatable
@Retention(AnnotationRetention.BINARY)
internal annotation class MergeInnerClassArrayInfo(
    val id: Byte,
    val value: Array<InnerClassArrayValue>
)