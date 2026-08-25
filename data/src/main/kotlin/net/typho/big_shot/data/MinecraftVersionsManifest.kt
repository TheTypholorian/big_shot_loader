package net.typho.big_shot.data

import net.typho.data_util.codec.Codec
import net.typho.data_util.impl.JsonFormat
import java.net.HttpURLConnection
import java.net.URI

data class MinecraftVersionsManifest(
    @JvmField
    val latest: Latest,
    @JvmField
    val versions: List<Version>
) {
    companion object {
        @JvmField
        val CODEC = Codec.reflect(MinecraftVersionsManifest::class.java)
        val INSTANCE by lazy {
            val connection = URI.create("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json").toURL().openConnection() as HttpURLConnection
            connection.requestMethod = "GET"

            if (connection.responseCode == 404) {
                throw RuntimeException("[Big Shot Lib] Unable to get Minecraft version manifest")
            }

            val body = connection.getInputStream().bufferedReader().use { it.readText() }
            JsonFormat().read(CODEC, body)
        }

        init {
            println(CODEC)
        }
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
