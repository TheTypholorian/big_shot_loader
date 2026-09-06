package net.typho.big_shot.merger

import net.typho.big_shot.data.Dist
import net.typho.big_shot.data.env.Environment
import net.typho.big_shot.data.env.EnvironmentRestriction
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
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
        val node = AnnotationNode("Lnet/typho/big_shot/data/env/EnvironmentRestriction;")
        node.values = listOf(
            "dist",
            arrayOf(
                "Lnet/typho/big_shot/data/Dist;",
                dist.name
            ),
            "deps",
            deps.map { dep ->
                val node = AnnotationNode($$"Lnet/typho/big_shot/data/env/EnvironmentRestriction$Dep;")
                node.values = listOf(
                    "name",
                    dep.name,
                    "range",
                    dep.range
                )
                node
            }
        )
        return node
    }

    @JvmStatic
    fun merge(name: String, classes: List<Branch>): ClassNode {
        if (classes.map { it.node.version }.toHashSet().size > 1) {
            throw IllegalArgumentException("Multiple class bytecode versions inputted for class merger, this is not allowed.")
        }

        val result = ClassNode()
        result.name = name

        fun <T : Any> mergeClassValue(
            property: KMutableProperty<T>,
            id: Byte,
            valueAnnoDesc: String,
            mergeInfoAnnoDesc: String,
            valueTransform: (T) -> Any = { it }
        ) {
            val values = classes.map { property.getter.call(it.node) }

            if (values.toHashSet().size > 1) {
                val node = AnnotationNode(mergeInfoAnnoDesc)
                node.values = listOf(
                    "id",
                    id,
                    "value",
                    values.mapIndexed { index, value ->
                        val node = AnnotationNode(valueAnnoDesc)
                        node.values = listOf(
                            "value",
                            valueTransform(value),
                            "for",
                            classes[index].env.toRestriction(true).toNode()
                        )
                        node
                    }
                )
                (result.visibleAnnotations ?: mutableListOf<AnnotationNode>().also { result.visibleAnnotations = it }).add(node)
            } else {
                property.setter.call(result, values.first())
            }
        }

        fun mergeIntClassValue(property: KMutableProperty<Int>, id: Byte) = mergeClassValue(property, id, "Lnet/typho/big_shot/merger/IntValue;", "Lnet/typho/big_shot/merger/MergeIntInfo;")

        mergeIntClassValue(ClassNode::access, ClassMergeInfoIds.ACCESS)

        return result
    }

    class TestA {
    }

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