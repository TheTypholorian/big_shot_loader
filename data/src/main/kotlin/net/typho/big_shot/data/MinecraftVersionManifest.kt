package net.typho.big_shot.data

import net.typho.data_util.codec.Codec

data class MinecraftVersionManifest(
    @JvmField
    val latest: Latest,
    @JvmField
    val versions: List<Version>
) {
    companion object {
        @JvmField
        val CODEC = Codec.reflect(MinecraftVersionManifest::class.java)
    }

    data class Latest(
        @JvmField
        val release: String,
        @JvmField
        val snapshot: String?
    ) {
        companion object {
            @JvmField
            val CODEC = Codec.reflect(Latest::class.java)
        }
    }

    data class Version(
        @JvmField
        val id: String,
        @JvmField
        val type: String,
        @JvmField
        val url: String,
        @JvmField
        val time: String?,
        @JvmField
        val releaseTime: String,
        @JvmField
        val sha1: String?,
        @JvmField
        val complianceLevel: Int?
    ) {
        companion object {
            @JvmField
            val CODEC = Codec.reflect(Version::class.java)
        }
    }
}
