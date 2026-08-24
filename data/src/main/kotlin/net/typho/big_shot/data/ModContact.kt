package net.typho.big_shot.data

import net.typho.data_util.codec.Codec

data class ModContact(
    @JvmField
    val discord: String? = null,
    @JvmField
    val email: String? = null,
    @JvmField
    val issues: String? = null,
    @JvmField
    val github: String? = null,
    @JvmField
    val website: String? = null
) {
    companion object {
        @JvmStatic
        val CODEC = Codec.reflect(ModContact::class.java)
    }
}
