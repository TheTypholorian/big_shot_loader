package net.typho.big_shot.data.env

import net.typho.big_shot.data.Dist

/**
 * Specifies an environment restriction to a file, class, field, or method.
 * This is similar to Fabric's `@Environment` and Forge's `@OnlyIn`, though this annotation also has support for dependency version checking.
 */
@Target(AnnotationTarget.FILE, AnnotationTarget.CLASS, AnnotationTarget.FIELD, AnnotationTarget.CONSTRUCTOR, AnnotationTarget.FUNCTION)
annotation class EnvironmentRestriction(
    val dist: Dist = Dist.UNIVERSAL,
    val deps: Array<Dep> = [],
    /**
     * Dictates how the restriction is applied.
     *
     * If `true`, the method is entirely removed if it does not match the current environment.
     *
     * If `false`, the method's body is replaced with an exception.
     */
    val delete: Boolean = false
) {
    @Target
    annotation class Dep(
        val name: String,
        /**
         * Should be in fabric-style comparator range notation (ex. `>1.21.1 <=1.21.11`).
         *
         * Forge-style interval notation (ex. `(1.21.1, 1.21.11]`) is not supported.
         */
        val range: String = "*"
    )

    companion object {
        @JvmStatic
        fun EnvironmentRestriction.isAllowed(env: Environment): Boolean {
            if (!dist.isAllowed(env.dist)) {
                return false
            }

            for (dep in deps) {
                val version = env.deps[dep.name] ?: return false

                if (!version.satisfies(dep.range)) {
                    return false
                }
            }

            return true
        }
    }
}
