package net.typho.big_shot.data.env

import net.typho.big_shot.data.Dist
import org.semver4j.Semver

data class Environment @JvmOverloads constructor(
    @JvmField
    val dist: Dist = Dist.UNIVERSAL,
    @JvmField
    val deps: Map<String, Semver> = mapOf()
) {
    @JvmOverloads
    fun toRestriction(delete: Boolean = false) = EnvironmentRestriction(dist, deps.map { (name, version) -> EnvironmentRestriction.Dep(name, version.version) }.toTypedArray(), delete)
}