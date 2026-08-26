package net.typho.big_shot.data

import net.typho.data_util.anno.InlineCodec
import net.typho.data_util.codec.Codec

import java.io.Serializable
import java.net.URL

data class MinecraftVersionFamily(
    @JvmField
    val versions: List<String>,
    @JvmField
    val url: URL,
    @JvmField
    val parent: MinecraftVersionManifest
) : Serializable {
    /**
     * The Minecraft version that is used in dev
     */
    @JvmField
    val primaryVersion: String = versions.last()
    /**
     * Additional Minecraft versions that have no modder-relevant change(s) (ex. patches)
     */
    @JvmField
    val additionalVersions: List<String> = versions.toMutableList().apply { removeLast() }
    val info by lazy { parent.familyInfo(this) }
    /**
     * The version of Parchment to use for this Minecraft version, in the format `("1.21.1", "2024.11.17")`.
     * Will only ever be null if this version is deobfuscated (>=26.1)
     * For Minecraft versions without an explicit parchment version (ex. 1.19, 1.19.1, 1.20, 1.21.2), it uses the next parchment version.
     * This results in all versions before 1.16.5 using 1.16.5 parchment, which is better than nothing.
     */
    val parchmentVersion: MinecraftVersionManifest.ParchmentVersion? by lazy { MinecraftVersionManifest.PARCHMENT_VERSIONS.firstOrNull { this <= it.minecraft } }
    val obfuscated: Boolean by lazy { this < "26.1" }

    operator fun compareTo(other: MinecraftVersionFamily) = parent.families.indexOfFirst { it.primaryVersion == primaryVersion }.compareTo(parent.families.indexOfFirst { it.primaryVersion == other.primaryVersion })

    operator fun compareTo(other: String) = parent.families.indexOfFirst { it.primaryVersion == primaryVersion }.compareTo(parent.families.indexOfFirst { it.versions.contains(other) })

    override fun toString(): String {
        return "MinecraftVersionFamily(primaryVersion='$primaryVersion', additionalVersions=$additionalVersions, ${if (obfuscated) "parchmentVersion=$parchmentVersion" else "deobfuscated"})"
    }

    data class Info(
        @JvmField
        val downloads: MCDownloads,
        @JvmField
        val id: String,
        @JvmField
        val javaVersion: JavaVersion?,
        @JvmField
        val libraries: List<Library>
    ) {
        companion object {
            @JvmField
            val CODEC = Codec.reflect(Info::class.java)
        }

        @InlineCodec(true)
        data class JavaVersion(
            @JvmField
            val majorVersion: Int
        ) {
            companion object {
                @JvmField
                val CODEC = Codec.reflect(JavaVersion::class.java)
            }
        }

        @InlineCodec(true)
        data class Download(
            @JvmField
            val url: String
        ) {
            companion object {
                @JvmField
                val CODEC = Codec.reflect(Download::class.java)
            }
        }

        @InlineCodec(true)
        data class MCDownloads(
            @JvmField
            val client: Download,
            @JvmField
            val server: Download?
        ) {
            companion object {
                @JvmField
                val CODEC = Codec.reflect(MCDownloads::class.java)
            }
        }

        @InlineCodec(true)
        data class Library(
            @JvmField
            val name: String
        ) {
            companion object {
                @JvmField
                val CODEC = Codec.reflect(Library::class.java)
            }
        }
    }
}