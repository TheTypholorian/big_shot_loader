package net.typho.big_shot.agent

import net.typho.asm_util.ClassTransformInfo
import net.typho.asm_util.error.ClassVisitException
import net.typho.asm_util.insn.InsnPointer
import net.typho.asm_util.method.MethodPointer
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.VarInsnNode
import java.lang.instrument.Instrumentation
import java.nio.file.Files
import java.nio.file.Paths
import java.util.jar.JarFile
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.outputStream
import kotlin.io.path.writeBytes

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
        path.writeBytes(bytes)
    }

    @OptIn(ExperimentalPathApi::class)
    @JvmStatic
    fun premain(args: String?, inst: Instrumentation) {
        DEBUG_PATH.deleteRecursively()

        val loader = Files.createTempDirectory("big_shot_agent").resolve("loader.jar")
        loader.outputStream().use { output ->
            javaClass.classLoader.getResourceAsStream("loader.jar").use { input ->
                input!!.copyTo(output)
            }
        }
        inst.appendToSystemClassLoaderSearch(JarFile(loader.toFile()))

        inst.addTransformer({ loader, className, classBeingRedefined, domain, bytes ->
            try {
                val info = ClassTransformInfo(bytes)

                when (className) {
                    "net/fabricmc/loader/impl/launch/knot/Knot" -> {
                        info.markChanged()
                        info.computeMaxStacks()

                        MethodPointer.method()
                            .name("<clinit>")
                            .findOrThrow(info.node) { method ->
                                method.instructions.insertBefore(
                                    InsnPointer.simple()
                                        .opcode(Opcodes.RETURN)
                                        .findOrThrow(method.instructions),
                                    MethodInsnNode(
                                        Opcodes.INVOKESTATIC,
                                        "net/typho/big_shot/loader/FabricHooks",
                                        "clinit",
                                        "()V"
                                    )
                                )
                            }
                        MethodPointer.method()
                            .name("init")
                            .desc("([Ljava/lang/String;)Ljava/lang/ClassLoader;")
                            .findOrThrow(info.node) { method ->
                                method.instructions.insert(
                                    InsnPointer.fieldSet()
                                        .owner(className)
                                        .name("provider")
                                        .findOrThrow(method.instructions),
                                    InsnList().apply {
                                        add(VarInsnNode(Opcodes.ALOAD, 0))
                                        add(FieldInsnNode(
                                            Opcodes.GETFIELD,
                                            className,
                                            "provider",
                                            "Lnet/fabricmc/loader/impl/game/GameProvider;"
                                        ))
                                        add(MethodInsnNode(
                                            Opcodes.INVOKESTATIC,
                                            "net/typho/big_shot/loader/FabricHooks",
                                            "loadGameProvider",
                                            "(Lnet/fabricmc/loader/impl/game/GameProvider;)V"
                                        ))
                                    }
                                )
                            }
                    }
                    "net/minecraft/client/Minecraft" -> {
                        info.markChanged()
                        info.computeMaxStacks()

                        val createTitle = MethodPointer.method()
                            .name("createTitle")
                            .findOrThrow(info.node)
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

                return@addTransformer info.compile(::debugSaveClass)
            } catch (t: Throwable) {
                ClassVisitException("Error while Big Shot Loader was transforming class $className", t).printStackTrace()

                return@addTransformer null
            }
        }, true)
    }
}