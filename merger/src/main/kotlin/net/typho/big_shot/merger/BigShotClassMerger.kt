package net.typho.big_shot.merger

import net.typho.big_shot.data.Dist
import net.typho.big_shot.data.env.Environment
import net.typho.big_shot.data.env.EnvironmentRestriction
import net.typho.big_shot.merger.AnnotationInfo.Companion.toNode
import net.typho.big_shot.merger.InnerClassInfo.Companion.toInfo
import net.typho.big_shot.merger.TypeAnnotationInfo.Companion.toTypeInfo
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InnerClassNode
import org.objectweb.asm.tree.TypeAnnotationNode
import java.io.File
import kotlin.reflect.KMutableProperty1

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

    private fun createMergeInfoAnno() {
    }

    @JvmStatic
    fun merge(name: String, classes: List<Branch>): ClassNode {
        if (classes.map { it.node.version }.toHashSet().size > 1) {
            throw IllegalArgumentException("Multiple class bytecode versions inputted for class merger, this is not allowed.")
        }

        val result = ClassNode()
        result.name = name

        fun <N : Any, T> mergeValue(
            nodes: List<N?>,
            property: KMutableProperty1<N, T>,
            onEqual: (T?) -> Unit,
            id: Byte,
            valueAnnoDesc: String,
            mergeInfoAnnoDesc: String,
            valueToAnno: (T?) -> Any? = { it },
            valueTransform: (T) -> T? = { it }
        ) {
            val values = nodes.map { it?.let { valueTransform(property.get(it)) } }

            if (values.toHashSet().size > 1) {
                val node = AnnotationNode(mergeInfoAnnoDesc).also {
                    it.values = listOf(
                        "id", id,
                        "value", values.mapIndexedNotNull { index, value ->
                            valueToAnno(value)?.let { value ->
                                AnnotationNode(valueAnnoDesc).also {
                                    it.values = listOf(
                                        "value", value,
                                        "for", classes[index].env.toRestriction(true).toNode()
                                    )
                                }
                            }
                        }
                    )
                }
                (result.visibleAnnotations ?: mutableListOf<AnnotationNode>().also { result.visibleAnnotations = it }).add(node)
            } else {
                onEqual(values.first())
            }
        }

        fun <N : Any> mergeIntValue(values: List<N?>, property: KMutableProperty1<N, Int>, onEqual: (Int?) -> Unit, id: Byte, valueTransform: (Int) -> Int? = { it }) {
            mergeValue(values, property, onEqual, id, "Lnet/typho/big_shot/merger/IntValue;", "Lnet/typho/big_shot/merger/MergeIntInfo;", valueTransform = valueTransform)
        }

        fun <N : Any> mergeStringValue(values: List<N?>, property: KMutableProperty1<N, String?>, onEqual: (String?) -> Unit, id: Byte, valueTransform: (String?) -> String? = { it }) {
            mergeValue(values, property, onEqual, id, "Lnet/typho/big_shot/merger/StringValue;", "Lnet/typho/big_shot/merger/MergeStringInfo;", valueToAnno = { it ?: "" }, valueTransform = valueTransform)
        }

        fun <N : Any> mergeStringArrayValue(values: List<N?>, property: KMutableProperty1<N, List<String>?>, onEqual: (List<String>?) -> Unit, id: Byte, valueTransform: (List<String>?) -> List<String>? = { it }) {
            mergeValue(values, property, onEqual, id, "Lnet/typho/big_shot/merger/StringArrayValue;", "Lnet/typho/big_shot/merger/MergeStringArrayInfo;", valueToAnno = { it ?: listOf<String>() }, valueTransform = valueTransform)
        }

        fun <N : Any> mergeAnnoArrayValue(values: List<N?>, property: KMutableProperty1<N, List<AnnotationNode>?>, onEqual: (List<AnnotationNode>?) -> Unit, id: Byte, valueTransform: (List<AnnotationNode>?) -> List<AnnotationNode>? = { it }) {
            mergeValue(values, property, onEqual, id, "Lnet/typho/big_shot/merger/AnnotationArrayValue;", "Lnet/typho/big_shot/merger/MergeAnnotationArrayInfo;", valueToAnno = { it ?: listOf<AnnotationNode>() }, valueTransform = valueTransform)
        }

        fun <N : Any> mergeTypeAnnoArrayValue(values: List<N?>, property: KMutableProperty1<N, List<TypeAnnotationNode>?>, onEqual: (List<TypeAnnotationNode>?) -> Unit, id: Byte, valueTransform: (List<TypeAnnotationNode>?) -> List<TypeAnnotationNode>? = { it }) {
            mergeValue(values, property, onEqual, id, "Lnet/typho/big_shot/merger/TypeAnnotationArrayValue;", "Lnet/typho/big_shot/merger/MergeTypeAnnotationArrayInfo;", valueToAnno = {
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
            }, valueTransform = valueTransform)
        }

        fun <N : Any> mergeInnerClassArrayValue(values: List<N?>, property: KMutableProperty1<N, List<InnerClassNode>?>, onEqual: (List<InnerClassNode>?) -> Unit, id: Byte, valueTransform: (List<InnerClassNode>?) -> List<InnerClassNode>? = { it }) {
            mergeValue(values, property, onEqual, id, "Lnet/typho/big_shot/merger/InnerClassArrayValue;", "Lnet/typho/big_shot/merger/MergeInnerClassArrayInfo;", valueToAnno = {
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
            }, valueTransform = valueTransform)
        }

        val nodes = classes.map { it.node }
        mergeIntValue(nodes, ClassNode::access, { result.access = it!! }, ClassMergeInfoIds.ACCESS) { it and (Opcodes.ACC_DEPRECATED or Opcodes.ACC_RECORD).inv() }
        mergeStringValue(nodes, ClassNode::signature, { result.signature = it!! }, ClassMergeInfoIds.SIGNATURE)
        mergeStringValue(nodes, ClassNode::superName, { result.superName = it!! }, ClassMergeInfoIds.SUPER_NAME)
        mergeStringArrayValue(nodes, ClassNode::interfaces, { result.interfaces = it?.toList() }, ClassMergeInfoIds.INTERFACES)
        mergeStringValue(nodes, ClassNode::sourceFile, { result.sourceFile = it!! }, ClassMergeInfoIds.SOURCE_FILE)
        mergeStringValue(nodes, ClassNode::sourceDebug, { result.sourceDebug = it!! }, ClassMergeInfoIds.SOURCE_DEBUG)
        mergeStringValue(nodes, ClassNode::outerClass, { result.outerClass = it!! }, ClassMergeInfoIds.OUTER_CLASS)
        mergeStringValue(nodes, ClassNode::outerMethod, { result.outerMethod = it!! }, ClassMergeInfoIds.OUTER_METHOD)
        mergeStringValue(nodes, ClassNode::outerMethodDesc, { result.outerMethodDesc = it!! }, ClassMergeInfoIds.OUTER_METHOD_DESC)
        mergeAnnoArrayValue(nodes, ClassNode::visibleAnnotations, { result.visibleAnnotations = it?.toList() }, ClassMergeInfoIds.VISIBLE_ANNOTATIONS)
        mergeAnnoArrayValue(nodes, ClassNode::invisibleAnnotations, { result.invisibleAnnotations = it?.toList() }, ClassMergeInfoIds.INVISIBLE_ANNOTATIONS)
        mergeTypeAnnoArrayValue(nodes, ClassNode::visibleTypeAnnotations, { result.visibleTypeAnnotations = it?.toList() }, ClassMergeInfoIds.VISIBLE_TYPE_ANNOTATIONS)
        mergeTypeAnnoArrayValue(nodes, ClassNode::invisibleTypeAnnotations, { result.invisibleTypeAnnotations = it?.toList() }, ClassMergeInfoIds.INVISIBLE_TYPE_ANNOTATIONS)
        mergeInnerClassArrayValue(nodes, ClassNode::innerClasses, { result.innerClasses = it?.toList() }, ClassMergeInfoIds.INNER_CLASSES)
        mergeStringArrayValue(nodes, ClassNode::nestMembers, { result.nestMembers = it?.toList() }, ClassMergeInfoIds.INNER_CLASSES)
        mergeStringArrayValue(nodes, ClassNode::permittedSubclasses, { result.permittedSubclasses = it?.toList() }, ClassMergeInfoIds.INNER_CLASSES)

        return result
    }

    class TestA<T> {
        fun test() {
            println(10)
        }
    }

    @Deprecated("")
    private class TestB {
        fun test() {
            println(30)
        }
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