package net.typho.big_shot.loader.util.mixin

import net.typho.asm_util.ASMUtil.kotlinMetadata
import net.typho.asm_util.ASMUtil.splice
import net.typho.asm_util.insn.InsnPointer
import net.typho.asm_util.method.MethodPointer
import net.typho.asm_util.remap.CompatClassRemapper
import net.typho.big_shot.loader.BigShotLoader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.commons.Remapper
import org.objectweb.asm.tree.ClassNode
import org.spongepowered.asm.service.MixinService
import kotlin.metadata.ClassKind
import kotlin.metadata.jvm.KotlinClassMetadata
import kotlin.metadata.kind

object KotlinMixinFixer {
    /**
     * If the mixin is in kotlin, this method fixes it so it works fine (specifically, static methods).
     *
     * If the mixin isn't in kotlin, nothing happens.
     *
     * @return If the class was changed
     */
    @Suppress("UNCHECKED_CAST")
    @JvmStatic
    fun fix(node: ClassNode): Boolean {
        val metadata = KotlinClassMetadata.readLenient(node.visibleAnnotations?.firstNotNullOfOrNull { it.kotlinMetadata } ?: return false)
        var changed = false

        if (metadata !is KotlinClassMetadata.Class) {
            println("Kotlin mixin ${node.name} must be a normal class for Big Shot to tweak it, got a ${metadata.javaClass.name}. No action will be taken, if there are problems with the mixin, this is likely a cause.")
            return false
        }

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

        metadata.kmClass.companionObject?.let { companion ->
            changed = true
            val fullCompanion = "${node.name}$$companion"
            node.fields.removeIf { it.name == "Companion" }

            when (metadata.kmClass.kind) {
                ClassKind.INTERFACE -> {
                    MethodPointer.method()
                        .name("<clinit>")
                        .findOrThrow(node) { method ->
                            method.instructions.splice(
                                InsnPointer.fieldGetStatic()
                                    .owner(fullCompanion)
                                    .desc("L$fullCompanion;"),
                                InsnPointer.fieldSetStatic()
                                    .owner(node.name)
                                    .name("Companion")
                                    .desc("L$fullCompanion;")
                            )
                        }
                }
                else -> {
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
                }
            }

            val companionRemapper = CompatClassRemapper(Opcodes.ASM9, node, object : Remapper(Opcodes.ASM9) {
                override fun map(internalName: String?): String? {
                    if (internalName == fullCompanion) {
                        return node.name
                    }

                    return super.map(internalName)
                }
            })
            val companionNode = MixinService.getService().bytecodeProvider.getClassNode(fullCompanion)

            for (field in companionNode.fields) {
                if (field.desc != "L$fullCompanion;") {
                    field.accept(companionRemapper)
                }
            }

            for (method in companionNode.methods) {
                if (method.name != "<init>" && method.name != "<clinit>" && !((method.access and Opcodes.ACC_SYNTHETIC) != 0 && method.name.startsWith("access$"))) {
                    node.methods.removeIf { it.name == method.name && it.desc == method.desc }
                    method.access = (method.access or Opcodes.ACC_STATIC) and Opcodes.ACC_FINAL.inv() and Opcodes.ACC_SYNTHETIC.inv()
                    method.accept(companionRemapper)
                }
            }
        }

        if (changed) {
            val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
            node.accept(writer)
            BigShotLoader.debugSaveClass(node.name, writer.toByteArray())
        }

        return changed
    }
}