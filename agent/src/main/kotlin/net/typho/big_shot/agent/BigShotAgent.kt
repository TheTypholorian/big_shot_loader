package net.typho.big_shot.agent

import net.typho.asm_util.ClassTransformInfo
import net.typho.asm_util.error.ClassVisitException
import net.typho.asm_util.insn.InsnPointer
import net.typho.asm_util.method.MethodPointer
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode
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

        val loaderPath = Files.createTempDirectory("big_shot_agent").resolve("loader.jar")
        loaderPath.outputStream().use { output ->
            javaClass.classLoader.getResourceAsStream("loader.jar").use { input ->
                input!!.copyTo(output)
            }
        }
        inst.appendToSystemClassLoaderSearch(JarFile(loaderPath.toFile()))

        inst.addTransformer({ loader, className, classBeingRedefined, domain, bytes ->
            try {
                val info = ClassTransformInfo.AgentTransform(bytes)

                when (className) {
                    "net/fabricmc/loader/impl/launch/knot/Knot" -> {
                        info.markChanged()
                        info.computeMaxStacks()

                        val bigShotLoader = loader.loadClass("net.typho.big_shot.loader.BigShotLoader")
                        bigShotLoader.getField("LOADER_PATH").set(null, loaderPath)
                        bigShotLoader.getField("INSTRUMENTATION").set(null, inst)
                        bigShotLoader.getMethod("onInstrumentationInit").invoke(null)

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
                                method.instructions.insert(
                                    InsnPointer.methodCallStatic()
                                        .owner("net/fabricmc/loader/impl/launch/FabricLauncherBase")
                                        .name("finishMixinBootstrapping")
                                        .findOrThrow(method.instructions),
                                    InsnList().apply {
                                        add(MethodInsnNode(
                                            Opcodes.INVOKESTATIC,
                                            "net/typho/big_shot/loader/FabricHooks",
                                            "registerMixins",
                                            "()V"
                                        ))
                                    }
                                )
                            }
                    }
                    "net/fabricmc/loader/impl/FabricLoaderImpl" -> {
                        info.markChanged()
                        info.computeMaxStacks()

                        MethodPointer.method()
                            .name("finishModLoading")
                            .findOrThrow(info.node) { method ->
                                method.instructions.insert(
                                    InsnList().apply {
                                        add(MethodInsnNode(
                                            Opcodes.INVOKESTATIC,
                                            "net/typho/big_shot/loader/FabricHooks",
                                            "finishModLoading",
                                            "()V"
                                        ))
                                    }
                                )
                            }
                    }
                    "org/spongepowered/asm/mixin/transformer/MixinInfo" -> {
                        info.markChanged()
                        info.computeMaxStacks()

                        MethodPointer.method()
                            .name("loadMixinClass")
                            .desc("(Ljava/lang/String;)Lorg/objectweb/asm/tree/ClassNode;")
                            .findOrThrow(info.node) { method ->
                                method.instructions.insertBefore(
                                    InsnPointer.simple()
                                        .opcode(Opcodes.ARETURN)
                                        .lastOrdinal()
                                        .findOrThrow(method.instructions),
                                    InsnList().apply {
                                        add(InsnNode(Opcodes.DUP))
                                        add(MethodInsnNode(
                                            Opcodes.INVOKESTATIC,
                                            "net/typho/big_shot/loader/BigShotLoader",
                                            "transformMixinClass",
                                            "(Lorg/objectweb/asm/tree/ClassNode;)V"
                                        ))
                                    }
                                )
                            }
                    }
                }

                return@addTransformer info.compile(::debugSaveClass)
            } catch (t: Throwable) {
                ClassVisitException("Error while Big Shot Agent was transforming class $className", t).printStackTrace()

                return@addTransformer null
            }
        }, true)
    }
}