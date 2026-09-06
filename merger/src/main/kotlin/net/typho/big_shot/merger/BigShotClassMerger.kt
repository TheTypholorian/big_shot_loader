package net.typho.big_shot.merger

import net.typho.big_shot.data.Dist
import net.typho.big_shot.data.env.Environment
import net.typho.big_shot.data.env.EnvironmentRestriction
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import java.io.File

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