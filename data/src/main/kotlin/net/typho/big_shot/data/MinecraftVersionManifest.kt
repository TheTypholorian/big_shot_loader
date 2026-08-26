package net.typho.big_shot.data

import net.typho.data_util.DataReadException
import net.typho.data_util.codec.Codec
import net.typho.data_util.impl.JsonFormat
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.time.Instant
import kotlin.collections.component1
import kotlin.collections.component2

data class MinecraftVersionManifest(
    @JvmField
    val latest: Latest,
    @JvmField
    val versions: List<Version>
) {
    companion object {
        const val URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"

        @JvmField
        val CODEC = Codec.reflect(MinecraftVersionManifest::class.java)
        @get:JvmName("getNetworkInstance")
        val NETWORK_INSTANCE by lazy {
            val start = System.currentTimeMillis()

            val connection = URI.create(URL).toURL().openConnection() as HttpURLConnection
            connection.requestMethod = "GET"

            if (connection.responseCode == 404) {
                throw RuntimeException("[Big Shot Lib] Unable to download Minecraft version manifest")
            }

            val body = connection.getInputStream().bufferedReader().use { it.readText() }
            val manifest = JsonFormat().read(CODEC, body)

            manifest.familyInfo = { family ->
                val connection = family.url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"

                if (connection.responseCode == 404) {
                    throw RuntimeException("[Big Shot Lib] Unable to download Minecraft version info for ${family.primaryVersion}")
                }

                val body = connection.getInputStream().bufferedReader().use { it.readText() }
                JsonFormat().read(MinecraftVersionFamily.Info.CODEC, body)
            }

            println("[Big Shot Lib] Took ${System.currentTimeMillis() - start} ms to download Minecraft versions")

            manifest
        }
        @JvmField
        val PARCHMENT_VERSIONS = listOf(
            ParchmentVersion("1.16.5", "2022.03.06"),
            ParchmentVersion("1.17.1", "2021.12.12"),
            ParchmentVersion("1.18.2", "2022.11.06"),
            ParchmentVersion("1.19.2", "2022.11.27"),
            ParchmentVersion("1.19.3", "2023.06.25"),
            ParchmentVersion("1.19.4", "2023.06.26"),
            ParchmentVersion("1.20.1", "2023.09.03"),
            ParchmentVersion("1.20.2", "2023.12.10"),
            ParchmentVersion("1.20.3", "2023.12.31"),
            ParchmentVersion("1.20.4", "2024.04.14"),
            ParchmentVersion("1.20.6", "2024.06.16"),
            ParchmentVersion("1.21.1", "2024.11.17"),
            ParchmentVersion("1.21.3", "2024.12.07"),
            ParchmentVersion("1.21.4", "2025.03.23"),
            ParchmentVersion("1.21.5", "2025.06.15"),
            ParchmentVersion("1.21.6", "2025.06.29"),
            ParchmentVersion("1.21.7", "2025.07.18"),
            ParchmentVersion("1.21.8", "2025.09.14"),
            ParchmentVersion("1.21.9", "2025.10.05"),
            ParchmentVersion("1.21.10", "2025.10.12"),
            ParchmentVersion("1.21.11", "2025.12.20")
        )

        @JvmOverloads
        @JvmStatic
        fun fromCache(folder: File, forceDownload: Boolean = false): MinecraftVersionManifest {
            val cache = folder.resolve("versions.json")
            val format = JsonFormat()

            var manifest: MinecraftVersionManifest? = null

            if (!forceDownload && cache.exists()) {
                try {
                    manifest = JsonFormat().read(CODEC, cache.readText())
                } catch (_: DataReadException) {
                } catch (_: IOException) {
                }
            }

            if (manifest == null) {
                val connection = URI.create(URL).toURL().openConnection() as HttpURLConnection
                connection.requestMethod = "GET"

                if (connection.responseCode == 404) {
                    throw RuntimeException("[Big Shot Lib] Unable to download Minecraft version manifest")
                }

                val body = connection.getInputStream().bufferedReader().use { it.readText() }
                manifest = format.read(CODEC, body)
                cache.parentFile.mkdirs()
                cache.createNewFile()
                cache.writeText(format.write(CODEC, manifest))
            }

            manifest.familyInfo = { family ->
                val cache = folder.resolve("versions").resolve("${family.primaryVersion}.json")

                var info: MinecraftVersionFamily.Info? = null

                if (!forceDownload && cache.exists()) {
                    try {
                        info = format.read(MinecraftVersionFamily.Info.CODEC, cache.readText())
                    } catch (_: DataReadException) {
                    } catch (_: IOException) {
                    }
                }

                if (info == null) {
                    val connection = family.url.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"

                    if (connection.responseCode == 404) {
                        throw RuntimeException("[Big Shot Lib] Unable to download Minecraft version info for ${family.primaryVersion}")
                    }

                    val body = connection.getInputStream().bufferedReader().use { it.readText() }
                    info = format.read(MinecraftVersionFamily.Info.CODEC, body)
                    cache.parentFile.mkdirs()
                    cache.createNewFile()
                    cache.writeText(format.write(MinecraftVersionFamily.Info.CODEC, info))
                }

                info
            }
            manifest.redownload = { fromCache(folder, true) }

            return manifest
        }
    }

    @Transient
    @JvmField
    val families: List<MinecraftVersionFamily>
    @Transient
    internal lateinit var familyInfo: (MinecraftVersionFamily) -> MinecraftVersionFamily.Info
    @Transient
    lateinit var redownload: () -> MinecraftVersionManifest

    init {
        val versions = versions
            .filter { it.type == "release" }
            .map { Triple(it.id, Instant.parse(it.releaseTime), it.url) }
            .sortedWith { a, b -> a.second.compareTo(b.second) }
            .associateTo(linkedMapOf()) { it.first to URI.create(it.third).toURL() }

        val groupedVersions = linkedMapOf<String, MutableList<String>>()
        val gameDropIndex = versions.keys.indexOfFirst { it == "26.1" }

        versions.entries.forEachIndexed { index, (version, url) ->
            val groupVersion = if (index > gameDropIndex) {
                val versionComponents = version.split('.')
                "${versionComponents[0]}.${versionComponents[1]}"
            } else {
                when (version) {
                    "1.19.1" -> "1.19"
                    "1.20.1" -> "1.20"
                    "1.21.1" -> "1.21"
                    else -> version
                }
            }

            groupedVersions.computeIfAbsent(groupVersion) { mutableListOf() }.add(version)
        }

        val result = mutableListOf<MinecraftVersionFamily>()

        for ((group, version) in groupedVersions) {
            result.add(MinecraftVersionFamily(version, versions[version.last()]!!, this))
        }

        families = result
    }

    fun getFamily(version: String): MinecraftVersionFamily {
        return families.firstOrNull { it.versions.contains(version) } ?: throw NullPointerException("Nonexistent Minecraft version '$version'")
    }

    data class ParchmentVersion(
        @JvmField
        val minecraft: String,
        @JvmField
        val parchment: String
    ) {
        override fun toString(): String {
            return "$minecraft:$parchment"
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
