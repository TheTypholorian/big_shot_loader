package net.typho.big_shot.agent

import net.typho.asm_util.ClassOutputInfo
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import java.lang.instrument.Instrumentation
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.createParentDirectories

object BigShotAgent {
    @JvmField
    val DEBUG_PATH = Paths.get(".big_shot_debug")

    @JvmStatic
    fun debugSaveClass(
        className: String,
        bytes: ByteArray
    ) {
        val path = DEBUG_PATH.resolve("$className.class")
        path.createParentDirectories()
        Files.write(path, bytes)
    }

    @JvmStatic
    fun couldTransformClass(className: String): Boolean {
        return className == "net/fabricmc/loader/impl/FabricLoaderImpl"
    }

    @JvmStatic
    fun premain(args: String?, inst: Instrumentation) {
        inst.addTransformer { loader, className, classBeingRedefined, domain, bytes ->
            if (couldTransformClass(className)) {
                val node = ClassNode()
                ClassReader(bytes).accept(node, 0)
                val info = ClassOutputInfo(className)

                if (className.equals("net/fabricmc/loader/impl/FabricLoaderImpl")) {
                    val bigShotLoader = loader.loadClass("net.typho.big_shot.agent.")
                }

                info.end()?.let {
                    val bytes = it.toByteArray()
                    debugSaveClass(className, bytes)
                    return@addTransformer bytes
                }
            }

            return@addTransformer null
        }
    }
}