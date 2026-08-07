package net.typho.big_shot.agent

import net.typho.asm_util.ClassOutputInfo
import net.typho.asm_util.insn.InsnPointer
import net.typho.asm_util.method.MethodPointer
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import java.lang.instrument.Instrumentation
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteRecursively

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
        // TODO
        return true//className == "net/fabricmc/loader/impl/game/minecraft/launchwrapper/FabricTweaker"
    }

    @OptIn(ExperimentalPathApi::class)
    @JvmStatic
    fun premain(args: String?, inst: Instrumentation) {
        DEBUG_PATH.deleteRecursively()

        println(inst.allLoadedClasses.filter { it.name.startsWith("net.fabricmc") || it.name.startsWith("org.spongepowered") })

        inst.addTransformer { loader, className, classBeingRedefined, domain, bytes ->
            if (couldTransformClass(className)) {
                val node = ClassNode()
                ClassReader(bytes).accept(node, 0)
                val info = ClassOutputInfo(className)

                when (className) {
                    "net/fabricmc/loader/impl/FabricLoaderImpl" -> {
                        info.markChanged()
                        info.computeFrames()

                        val bigShotInit = loader.loadClass("net.typho.big_shot.agent.BigShotInit")
                        bigShotInit.getField("INSTRUMENTATION").set(null, inst)

                        val clinit = MethodPointer.method()
                            .name("<clinit>")
                            .find(node)
                            .orElseGet {
                                val method = MethodNode(
                                    Opcodes.ASM9,
                                    Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
                                    "<clinit>",
                                    "()V",
                                    null,
                                    null
                                )
                                node.methods.add(method)
                                method
                            }
                        clinit.instructions.insert(MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            "net/typho/big_shot/agent/BigShotInit",
                            "init",
                            "()V"
                        ))
                    }
                    "net/minecraft/client/Minecraft" -> {
                        info.markChanged()
                        info.computeFrames()

                        val createTitle = MethodPointer.method()
                            .name("createTitle")
                            .findOrThrow(node)
                        val toString = InsnPointer.methodCallInherited()
                            .name("toString")
                            .owner("java/lang/StringBuilder")
                            .lastOrdinal()
                            .findOrThrow(createTitle.instructions)

                        val insns = InsnList()
                        insns.add(LdcInsnNode(" + Big Shot Loader"))
                        insns.add(MethodInsnNode(
                            Opcodes.INVOKEVIRTUAL,
                            "java/lang/StringBuilder",
                            "append",
                            "(Ljava/lang/String;)Ljava/lang/StringBuilder;"
                        ))
                        createTitle.instructions.insertBefore(toString, insns)
                    }
                }

                info.end()?.let {
                    node.accept(it)
                    val bytes = it.toByteArray()
                    debugSaveClass(className, bytes)
                    return@addTransformer bytes
                }
            }

            return@addTransformer null
        }
    }
}