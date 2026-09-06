package net.typho.big_shot.data

import net.typho.data_util.codec.Codec

enum class Dist {
    UNIVERSAL,
    CLIENT,
    SERVER;

    fun isAllowed(other: Dist) = this == UNIVERSAL || other == UNIVERSAL || this == other

    companion object {
        @JvmField
        val CODEC = Codec.enumCodec(Dist::class.java)
    }
}