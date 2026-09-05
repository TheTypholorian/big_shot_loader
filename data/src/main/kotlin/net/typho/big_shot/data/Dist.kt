package net.typho.big_shot.data

import net.typho.data_util.codec.Codec

enum class Dist {
    CLIENT,
    SERVER;

    companion object {
        @JvmStatic
        val CODEC = Codec.enumCodec(Dist::class.java)
        @JvmStatic
        val NULLABLE_CODEC = Codec.STRING.map(
            { if (it == "*") null else Dist.valueOf(it.uppercase()) },
            { it?.name?.lowercase() ?: "*" }
        )
    }
}