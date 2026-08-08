package net.typho.big_shot.loader

import java.lang.instrument.Instrumentation
import java.nio.file.Paths
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeBytes

object BigShotLoader {
    @get:JvmName("getInstrumentation")
    lateinit var INSTRUMENTATION: Instrumentation
    @JvmField
    val DEBUG_PATH = Paths.get(".big_shot_debug")

    @JvmStatic
    fun debugSaveClass(
        className: String,
        bytes: ByteArray
    ) {
        val path = DEBUG_PATH.resolve("$className.class")
        path.createParentDirectories()
        path.writeBytes(bytes)
    }

    @JvmStatic
    fun onLoaderInit() {
        println("yay loader init! $INSTRUMENTATION")
    }
}