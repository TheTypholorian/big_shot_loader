package net.typho.big_shot.data

import net.typho.data_util.codec.Codec
import net.typho.data_util.impl.JsonFormat

import java.io.Serializable
import java.net.HttpURLConnection
import java.net.URI
import java.time.Instant
import kotlin.sequences.sortedWith

data class TargetMinecraftVersion(
    @JvmField
    val versions: List<String>,
    @JvmField
    val info: Info
) : Serializable {
    /**
     * The Minecraft version range for fabric/quilt
     */
    @Transient
    val fabricVersionRange: String = if (versions.size == 1) versions.first() else ">=${versions.first()} <=${versions.last()}"
    /**
     * The Minecraft version range for neoforge/forge
     */
    @Transient
    val forgeVersionRange: String = if (versions.size == 1) "[${versions.first()}]" else "[${versions.first()}, ${versions.last()}]"
    /**
     * The Minecraft version that is used in dev
     */
    @Transient
    val primaryVersion: String = versions.last()
    /**
     * Additional Minecraft versions that have no modder-relevant change(s) (ex. patches)
     */
    @Transient
    val additionalVersions: List<String> = versions.toMutableList().apply { removeLast() }
    /**
     * The version of Parchment to use for this Minecraft version, in the format `("1.21.1", "2024.11.17")`.
     * Might be null if Parchment doesn't support this version.
     * For Minecraft versions without an explicit parchment version (ex. 1.19, 1.19.1, 1.20, 1.21.2), it uses the next parchment version.
     * This results in all versions before 1.16.5 using 1.16.5 parchment, which is probably fine.
     *
     * **Note**: This value is not cached, so you can register extra parchment versions by putting `MCVersion.registerParchment(mc, parchment)` at the start of your build script.
     */
    val parchmentVersion: Pair<String, String>?
        get() = PARCHMENT_VERSIONS.sortedWith { a, b -> b.first.compareTo(a.first) }.firstOrNull { (mc, parchment) -> this >= mc }

    operator fun compareTo(other: TargetMinecraftVersion) = VERSIONS.indexOf(this).compareTo(VERSIONS.indexOf(other))

    operator fun compareTo(other: String) = compareTo(TargetMinecraftVersion[other])

    /*
    fun getVersionRange(loader: ModLoader) = when (loader) {
        ModLoader.FABRIC -> fabricVersionRange
        ModLoader.NEOFORGE, ModLoader.FORGE -> forgeVersionRange
        else -> null
    }
     */

    override fun toString(): String {
        return "MCVersion(primaryVersion='$primaryVersion', additionalVersions=$additionalVersions, parchmentVersion=${parchmentVersion?.let { "${it.first}:${it.second}" }}, fabricVersionRange='$fabricVersionRange', forgeVersionRange='$forgeVersionRange')"
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

        data class JavaVersion(
            @JvmField
            val component: String,
            @JvmField
            val majorVersion: Int
        ) {
            companion object {
                @JvmField
                val CODEC = Codec.reflect(JavaVersion::class.java)
            }
        }

        data class Download(
            @JvmField
            val sha1: String?,
            @JvmField
            val size: Int,
            @JvmField
            val url: String
        ) {
            companion object {
                @JvmField
                val CODEC = Codec.reflect(Download::class.java)
            }
        }

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

    companion object {
        /**
         * Most recent versions are at the start of the list
         */
        @JvmField
        val VERSIONS = mutableListOf<TargetMinecraftVersion>()
        /**
         * Most recent versions are at the start of the list
         */
        @JvmField
        val VERSION_SORT_ORDER = mutableListOf<String>()
        @JvmField
        val PARCHMENT_VERSIONS = mutableListOf<Pair<String, String>>()

        @JvmStatic
        fun registerParchment(mc: String, parchment: String) {
            PARCHMENT_VERSIONS.add(mc to parchment)
            PARCHMENT_VERSIONS.sortWith(Comparator.comparingInt { VERSION_SORT_ORDER.indexOf(it.first) })
        }

        @JvmStatic
        fun registerParchment(vararg entries: Pair<String, String>) {
            PARCHMENT_VERSIONS.addAll(entries)
            PARCHMENT_VERSIONS.sortWith(Comparator.comparingInt { VERSION_SORT_ORDER.indexOf(it.first) })
        }

        init {
            val versions = MinecraftVersionsManifest.INSTANCE.versions
                .asSequence()
                .filter { it.type == "release" }
                .map { Triple(it.id, Instant.parse(it.releaseTime), it.url) }
                .sortedWith { a, b -> a.second.compareTo(b.second) }
                .map { it.first to it.third }
                .toList()
            versions.mapTo(VERSION_SORT_ORDER) { it.first }

            val multiVersions = linkedMapOf<String, Pair<MutableList<String>, Info>>()
            val gameDropIndex = versions.indexOfFirst { it.first == "26.1" }

            versions.forEachIndexed { index, (version, url) ->
                val groupVersion = if (index > gameDropIndex) {
                    val versionComponents = version.split('.')
                    "${versionComponents[0]}.${versionComponents[1]}"
                } else {
                    when (version) {
                        "1.19" -> "1.19.1"
                        "1.20" -> "1.20.1"
                        "1.21" -> "1.21.1"
                        else -> version
                    }
                }

                multiVersions.computeIfAbsent(groupVersion) {
                    val connection = URI.create(url).toURL().openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"

                    if (connection.responseCode == 404) {
                        throw RuntimeException("[Big Shot Lib] Unable to get Minecraft version data for $version")
                    }

                    val body = connection.getInputStream().bufferedReader().use { it.readText() }
                    println(version)
                    val info = JsonFormat().read(Info.CODEC, body)

                    mutableListOf<String>() to info
                }.first.add(version)
            }

            for ((version, info) in multiVersions.values) {
                VERSIONS.add(TargetMinecraftVersion(version, info))
            }

            VERSIONS.reverse()

            registerParchment(
                "1.16.5" to "2022.03.06",
                "1.17.1" to "2021.12.12",
                "1.18.2" to "2022.11.06",
                "1.19.2" to "2022.11.27",
                "1.19.3" to "2023.06.25",
                "1.19.4" to "2023.06.26",
                "1.20.1" to "2023.09.03",
                "1.20.2" to "2023.12.10",
                "1.20.3" to "2023.12.31",
                "1.20.4" to "2024.04.14",
                "1.20.6" to "2024.06.16",
                "1.21.1" to "2024.11.17",
                "1.21.3" to "2024.12.07",
                "1.21.4" to "2025.03.23",
                "1.21.5" to "2025.06.15",
                "1.21.6" to "2025.06.29",
                "1.21.7" to "2025.07.18",
                "1.21.8" to "2025.09.14",
                "1.21.9" to "2025.10.05",
                "1.21.10" to "2025.10.12",
                "1.21.11" to "2025.12.20"
            )
        }

        @JvmStatic
        operator fun get(version: String): TargetMinecraftVersion {
            val version = version.substringBeforeLast(".0")
            return VERSIONS.firstOrNull { it.versions.contains(version) }
                ?: throw NullPointerException("Nonexistent Minecraft version '$version' (it should be in the format '1.21', '1.21.1', '26.1.2', etc.)")
        }

        @JvmStatic
        fun getMinJavaVersion(versions: Iterable<String>): Int {
            return versions.minOfOrNull { get(it).info.javaVersion?.majorVersion ?: 8 } ?: 8
        }
    }
}