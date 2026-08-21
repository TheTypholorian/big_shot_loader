package net.typho.big_shot.data

data class BigShotModData(
    @JvmField
    val id: String,
    @JvmField
    val version: String,
    @JvmField
    val dist: Dist,

    val name: DisplayedText,
    @JvmField
    val description: DisplayedText,
    @JvmField
    val authors: DisplayedText,
    @JvmField
    val license: String,
    @JvmField
    val icon: String
)