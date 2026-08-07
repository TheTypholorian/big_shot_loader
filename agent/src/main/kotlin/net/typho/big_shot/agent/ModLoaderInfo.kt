package net.typho.big_shot.agent

data class ModLoaderInfo(
    @JvmField
    val loader: ModLoader,
    @JvmField
    val version: String,
    @JvmField
    val detectedOther: MutableSet<ModLoader>
)
