package net.typho.big_shot.data

import net.typho.data_util.codec.Codec

data class BigShotModData(
    @JvmField
    val id: String,
    @JvmField
    val version: String,
    @JvmField
    val dist: Dist? = null,

    @JvmField
    val name: DisplayedText? = null,
    @JvmField
    val description: DisplayedText? = null,
    @JvmField
    val authors: DisplayedText? = null,
    @JvmField
    val license: String? = null,
    @JvmField
    val icon: String? = null,
    @JvmField
    val contact: ModContact? = null
) {
    companion object {
        @JvmStatic
        val CODEC = Codec.reflect(BigShotModData::class.java)
    }
}