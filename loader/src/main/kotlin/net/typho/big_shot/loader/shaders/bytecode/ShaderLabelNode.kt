package net.typho.big_shot.loader.shaders.bytecode

open class ShaderLabelNode @JvmOverloads constructor(
    @JvmField
    var name: String? = null
) {
    @JvmField
    var id: Int? = null

    @JvmOverloads
    fun getNameInsn(builder: ShaderBytecodeBuilder? = null): ShaderInsnNode? {
        return name?.let { ShaderInsnNode(OP_NAME, getId(builder), it) }
    }

    @JvmOverloads
    fun getId(builder: ShaderBytecodeBuilder? = null): Int {
        if (builder != null && id == null) {
            builder.labels.add(this)
            val id = builder.labelCounter++
            this.id = id
            return id
        }

        return id ?: throw NullPointerException("$this has not been assigned an id yet")
    }

    override fun toString(): String {
        return buildString {
            append("ShaderLabelNode")
            name?.let { append(" $it") }
            id?.let { append(" id=$it") }
        }
    }
}