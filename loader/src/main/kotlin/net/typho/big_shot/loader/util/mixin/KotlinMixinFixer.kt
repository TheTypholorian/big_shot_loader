package net.typho.big_shot.loader.util.mixin

import net.fabricmc.loader.impl.launch.FabricLauncherBase
import net.typho.asm_util.ASMUtil.splice
import net.typho.asm_util.insn.InsnPointer
import net.typho.asm_util.method.MethodPointer
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.util.TraceClassVisitor
import org.spongepowered.asm.service.MixinService
import java.io.PrintWriter
import kotlin.metadata.ClassKind
import kotlin.metadata.jvm.KotlinClassMetadata
import kotlin.metadata.kind

object KotlinMixinFixer {
    /**
     * @return If the class was changed
     */
    @Suppress("UNCHECKED_CAST")
    @JvmStatic
    fun fix(node: ClassNode): Boolean {
        var header: Metadata? = null

        for (anno in (node.visibleAnnotations ?: return false)) {
            if (anno.desc == "Lkotlin/Metadata;") {
                var kind = 1
                var metadataVersion = intArrayOf()
                var bytecodeVersion = intArrayOf(1, 0, 3)
                var data1 = arrayOf<String>()
                var data2 = arrayOf<String>()
                var extraString = ""
                var packageName = ""
                var extraInt = 0

                anno.values.chunked(2).forEach { (name, value) ->
                    when (name) {
                        "k" -> kind = value as Int
                        "mv" -> metadataVersion = (value as List<Int>).toIntArray()
                        "bv" -> bytecodeVersion = (value as List<Int>).toIntArray()
                        "d1" -> data1 = (value as List<String>).toTypedArray()
                        "d2" -> data2 = (value as List<String>).toTypedArray()
                        "xs" -> extraString = value as String
                        "pn" -> packageName = value as String
                        "xi" -> extraInt = value as Int
                    }
                }

                header = Metadata(kind, metadataVersion, bytecodeVersion, data1, data2, extraString, packageName, extraInt)
                break
            }
        }

        val metadata = KotlinClassMetadata.readLenient(header ?: return false)
        var changed = false

        if (metadata !is KotlinClassMetadata.Class) {
            println("Kotlin mixin ${node.name} must be a normal class for Big Shot to tweak it, got a ${metadata.javaClass.name}. No action will be taken, if there are problems with the mixin, this is likely a cause.")
            return false
        }

        println("Fixing Kotlin mixin ${node.name}")

        if (metadata.kmClass.kind == ClassKind.OBJECT) {
            changed = true
            node.fields.removeIf { it.name == "INSTANCE" }
            MethodPointer.method()
                .name("<clinit>")
                .findOrThrow(node) { method ->
                    method.instructions.splice(
                        InsnPointer.type(node.name),
                        InsnPointer.fieldSetStatic()
                            .owner(node.name)
                            .name("INSTANCE")
                            .desc("L${node.name};")
                    )
                }
        }

        println(metadata.kmClass.companionObject)
        metadata.kmClass.companionObject?.let { companion ->
            changed = true
            val fullCompanion = "${node.name}$$companion"
            node.fields.removeIf { it.name == "Companion" }

            MethodPointer.method()
                .name("<clinit>")
                .findOrThrow(node) { method ->
                    method.instructions.splice(
                        InsnPointer.type(fullCompanion),
                        InsnPointer.fieldSetStatic()
                            .owner(node.name)
                            .name("Companion")
                            .desc("L$fullCompanion;")
                    )
                }

            val companionNode = MixinService.getService().bytecodeProvider.getClassNode(fullCompanion)

            // TODO copy over fields

            for (method in companionNode.methods) {
                if (method.name != "<init>" && method.name != "<clinit>" && !((method.access and Opcodes.ACC_SYNTHETIC) != 0 && method.name.startsWith("access$"))) {
                    node.methods.removeIf { it.name == method.name && it.desc == method.desc }
                    method.access = method.access or Opcodes.ACC_STATIC
                    node.methods.add(method)
                }
            }
        }

        node.accept(TraceClassVisitor(PrintWriter(System.out)))

        return changed
    }
}