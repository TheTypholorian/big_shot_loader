package net.typho.big_shot.data

import net.typho.data_util.codec.Codec

enum class Dist {
    CLIENT,
    SERVER;

    companion object {
        @JvmStatic
        val CODEC = Codec.enumCodec(Dist::class.java)
    }
}