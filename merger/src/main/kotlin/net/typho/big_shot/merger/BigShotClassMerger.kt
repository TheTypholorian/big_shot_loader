package net.typho.big_shot.merger

import net.typho.big_shot.data.Dist
import net.typho.big_shot.data.env.Environment
import net.typho.big_shot.data.env.EnvironmentRestriction
import net.typho.big_shot.merger.AnnotationInfo.Companion.toNode
import net.typho.big_shot.merger.InnerClassInfo.Companion.toInfo
import net.typho.big_shot.merger.TypeAnnotationInfo.Companion.toTypeInfo
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InnerClassNode
import org.objectweb.asm.tree.TypeAnnotationNode
import java.io.File
import kotlin.reflect.KMutableProperty

object BigShotClassMerger {
    data class Branch(
        @JvmField
        val node: ClassNode,
        @JvmField
        val env: Environment
    )

    @JvmStatic
    fun EnvironmentRestriction.toNode(): AnnotationNode {
        return AnnotationNode("Lnet/typho/big_shot/data/env/EnvironmentRestriction;").also {
            it.values = listOf(
                "dist", arrayOf(
                    "Lnet/typho/big_shot/data/Dist;",
                    dist.name
                ),
                "deps", deps.map { dep ->
                    AnnotationNode($$"Lnet/typho/big_shot/data/env/EnvironmentRestriction$Dep;").also {
                        it.values = listOf(
                            "name", dep.name,
                            "range", dep.range
                        )
                    }
                }
            )
        }
    }

    @JvmStatic
    fun merge(name: String, classes: List<Branch>): ClassNode {
        if (classes.map { it.node.version }.toHashSet().size > 1) {
            throw IllegalArgumentException("Multiple class bytecode versions inputted for class merger, this is not allowed.")
        }

        val result = ClassNode()
        result.name = name

        fun <T> mergeClassValue(
            property: KMutableProperty<T>,
            id: Byte,
            valueAnnoDesc: String,
            mergeInfoAnnoDesc: String,
            valueTransform: (T) -> Any = { it!! }
        ) {
            val values = classes.map { property.getter.call(it.node) }

            if (values.toHashSet().size > 1) {
                val node = AnnotationNode(mergeInfoAnnoDesc).also {
                    it.values = listOf(
                        "id", id,
                        "value", values.mapIndexed { index, value ->
                            AnnotationNode(valueAnnoDesc).also {
                                it.values = listOf(
                                    "value", valueTransform(value),
                                    "for", classes[index].env.toRestriction(true).toNode()
                                )
                            }
                        }
                    )
                }
                (result.visibleAnnotations ?: mutableListOf<AnnotationNode>().also { result.visibleAnnotations = it }).add(node)
            } else {
                property.setter.call(result, values.first())
            }
        }

        fun mergeIntClassValue(property: KMutableProperty<Int>, id: Byte) {
            mergeClassValue(property, id, "Lnet/typho/big_shot/merger/IntValue;", "Lnet/typho/big_shot/merger/MergeIntInfo;")
        }

        fun mergeStringClassValue(property: KMutableProperty<String?>, id: Byte) {
            mergeClassValue(property, id, "Lnet/typho/big_shot/merger/StringValue;", "Lnet/typho/big_shot/merger/MergeStringInfo;") { it ?: "" }
        }

        fun mergeStringArrayClassValue(property: KMutableProperty<List<String>?>, id: Byte) {
            mergeClassValue(property, id, "Lnet/typho/big_shot/merger/StringArrayValue;", "Lnet/typho/big_shot/merger/MergeStringArrayInfo;") { it ?: listOf<String>() }
        }

        fun mergeAnnoArrayClassValue(property: KMutableProperty<List<AnnotationNode>?>, id: Byte) {
            mergeClassValue(property, id, "Lnet/typho/big_shot/merger/AnnotationArrayValue;", "Lnet/typho/big_shot/merger/MergeAnnotationArrayInfo;") { it ?: listOf<AnnotationNode>() }
        }

        fun mergeTypeAnnoArrayClassValue(property: KMutableProperty<List<TypeAnnotationNode>?>, id: Byte) {
            mergeClassValue(property, id, "Lnet/typho/big_shot/merger/TypeAnnotationArrayValue;", "Lnet/typho/big_shot/merger/MergeTypeAnnotationArrayInfo;") {
                it?.map { anno ->
                    val info = anno.toTypeInfo()
                    AnnotationNode("Lnet/typho/big_shot/merger/TypeAnnotationInfo;").also {
                        it.values = listOf(
                            "typeRef", info.typeRef,
                            "typePath", info.typePath,
                            "anno", info.anno.toNode()
                        )
                    }
                } ?: listOf<AnnotationNode>()
            }
        }

        fun mergeInnerClassArrayClassValue(property: KMutableProperty<List<InnerClassNode>?>, id: Byte) {
            mergeClassValue(property, id, "Lnet/typho/big_shot/merger/InnerClassArrayValue;", "Lnet/typho/big_shot/merger/MergeInnerClassArrayInfo;") {
                it?.map { inner ->
                    val info = inner.toInfo()
                    AnnotationNode("Lnet/typho/big_shot/merger/InnerClassInfo;").also {
                        it.values = listOf(
                            "name", info.name,
                            "outerName", info.outerName,
                            "innerName", info.innerName,
                            "access", info.access
                        )
                    }
                } ?: listOf<AnnotationNode>()
            }
        }

        mergeIntClassValue(ClassNode::access, ClassMergeInfoIds.ACCESS)
        mergeStringClassValue(ClassNode::signature, ClassMergeInfoIds.SIGNATURE)
        mergeStringClassValue(ClassNode::superName, ClassMergeInfoIds.SUPER_NAME)
        mergeStringArrayClassValue(ClassNode::interfaces, ClassMergeInfoIds.INTERFACES)
        mergeStringClassValue(ClassNode::sourceFile, ClassMergeInfoIds.SOURCE_FILE)
        mergeStringClassValue(ClassNode::sourceDebug, ClassMergeInfoIds.SOURCE_DEBUG)
        mergeStringClassValue(ClassNode::outerClass, ClassMergeInfoIds.OUTER_CLASS)
        mergeStringClassValue(ClassNode::outerMethod, ClassMergeInfoIds.OUTER_METHOD)
        mergeStringClassValue(ClassNode::outerMethodDesc, ClassMergeInfoIds.OUTER_METHOD_DESC)
        mergeAnnoArrayClassValue(ClassNode::visibleAnnotations, ClassMergeInfoIds.VISIBLE_ANNOTATIONS)
        mergeAnnoArrayClassValue(ClassNode::invisibleAnnotations, ClassMergeInfoIds.INVISIBLE_ANNOTATIONS)
        mergeTypeAnnoArrayClassValue(ClassNode::visibleTypeAnnotations, ClassMergeInfoIds.VISIBLE_TYPE_ANNOTATIONS)
        mergeTypeAnnoArrayClassValue(ClassNode::invisibleTypeAnnotations, ClassMergeInfoIds.INVISIBLE_TYPE_ANNOTATIONS)
        mergeInnerClassArrayClassValue(ClassNode::innerClasses, ClassMergeInfoIds.INNER_CLASSES)
        mergeStringArrayClassValue(ClassNode::nestMembers, ClassMergeInfoIds.INNER_CLASSES)
        mergeStringArrayClassValue(ClassNode::permittedSubclasses, ClassMergeInfoIds.INNER_CLASSES)

        return result
    }

    class TestA<T> {
    }

    @Deprecated("")
    private class TestB {
    }

    @JvmStatic
    fun main(args: Array<String>) {
        fun read(name: String): ClassNode {
            val reader = ClassReader(File("merger/build/classes/kotlin/main/net/typho/big_shot/merger/BigShotClassMerger$$name.class").readBytes())
            val node = ClassNode()
            reader.accept(node, 0)
            return node
        }

        val result = merge("MergedOutput", listOf(Branch(read("TestA"), Environment(Dist.CLIENT)), Branch(read("TestB"), Environment(Dist.SERVER))))
        val writer = ClassWriter(0)
        result.accept(writer)
        File("MergedOutput.class").writeBytes(writer.toByteArray())
    }
}