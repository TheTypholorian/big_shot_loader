package net.typho.big_shot.loader.shaders.reflect

data class AttributeName(
    @JvmField
    val owner: String,
    @JvmField
    val name: String,
    @JvmField
    val desc: String
) {
    override fun toString(): String {
        return "$owner.$name$desc"
    }
}