package net.typho.big_shot.data

import net.typho.data_util.anno.InlineCodec
import net.typho.data_util.codec.Codec

@InlineCodec
data class ModAuthor(
    @JvmField
    val name: DisplayedText,
    @JvmField
    val uuid: String? = null,
    @JvmField
    val contact: ModContact? = null
) {
    companion object {
        @JvmStatic
        val CODEC = Codec.reflect(ModAuthor::class.java)
    }
}
