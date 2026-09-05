package net.typho.big_shot.data

import net.typho.data_util.anno.FieldCodec
import net.typho.data_util.anno.FieldDefault
import net.typho.data_util.codec.Codec
import net.typho.data_util.codec.VersionedCodec

interface FabricModData {
    val schemaVersion: Int
    val id: String
    val version: String

    data class V0(
        override val id: String,
        override val version: String,
        @FieldCodec(owner = FabricModData::class, value = "DEPENDENCIES_CODEC")
        @JvmField
        val requires: Map<String, List<String>>?,
        @FieldCodec(owner = FabricModData::class, value = "DEPENDENCIES_CODEC")
        @JvmField
        val conflicts: Map<String, List<String>>?,
        @FieldCodec(owner = V0::class, value = "MIXINS_CODEC")
        @JvmField
        val mixins: Map<MixinEnvironment, List<String>>?,
        @JvmField
        val side: String?,
        @JvmField
        val initializer: String?,
        @JvmField
        val initializers: List<String>?,
        @JvmField
        val name: String?,
        @JvmField
        val description: String?,
        @FieldCodec(owner = FabricModData::class, value = "DEPENDENCIES_CODEC")
        @JvmField
        val recommends: Map<String, List<String>>?,
        @JvmField
        val authors: List<Person>?,
        @JvmField
        val contributors: List<Person>?,
        @JvmField
        val links: Links?,
        @JvmField
        val license: String?
    ) : FabricModData {
        override val schemaVersion: Int
            get() = 0

        enum class MixinEnvironment {
            CLIENT, COMMON, SERVER
        }

        data class Person(
            @JvmField
            val name: String?,
            @JvmField
            val email: String?,
            @JvmField
            val website: String?
        ) {
            companion object {
                @JvmField
                val CODEC = Codec.either(Codec.reflect(Person::class.java), listOf(Codec.STRING.mapRead { Person(it, null, null) }))
            }
        }

        data class Links(
            @JvmField
            val homepage: String?,
            @JvmField
            val issues: String?,
            @JvmField
            val sources: String?
        ) {
            companion object {
                @JvmField
                val CODEC = Codec.either(Codec.reflect(Links::class.java), listOf(Codec.STRING.mapRead { Links(it, null, null) }))
            }
        }

        companion object {
            @JvmField
            val MIXINS_CODEC = Codec.unboundedMap(Codec.STRING.listOf()).map(
                { it.mapKeys { (key, value) -> MixinEnvironment.valueOf(key.uppercase()) } },
                { it.mapKeys { (key, value) -> key.name.lowercase() } }
            )
            @JvmField
            val CODEC = Codec.reflect(V0::class.java)
        }
    }

    data class V1(
        override val id: String,
        override val version: String
    ) : FabricModData {
        override val schemaVersion: Int
            get() = 1

        companion object {
            @JvmField
            val CODEC = Codec.reflect(V1::class.java)
        }
    }

    companion object {
        @JvmField
        val DEPENDENCIES_CODEC: Codec<Map<String, List<String>>> = Codec.unboundedMap(Codec.either(Codec.STRING.listOf(), listOf(Codec.STRING.mapRead { listOf(it) })))
        @JvmField
        val VERSIONS = mutableMapOf(
            0 to V0.CODEC,
            1 to V1.CODEC
        )
        @JvmField
        val CODEC = VersionedCodec.of("schemaVersion", Codec.INT, { it.schemaVersion }, { VERSIONS[it]!! }, 0)
    }
}