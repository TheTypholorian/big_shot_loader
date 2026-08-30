package net.typho.big_shot.decompiler

import net.typho.asm_util.method.MethodPointer.Companion.method
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import java.io.File
import javax.management.Query.and

open class BigShotDecompiler(
    @JvmField
    val node: ClassNode
) {
    @JvmField
    val shortName: String
    @JvmField
    val pkg: String?
    @JvmField
    val imports = mutableSetOf<String>()

    init {
        val name = node.name.replace('/', '.')
        val dot = name.lastIndexOf('.')
        val dollar = name.lastIndexOf('$')

        if (dot == -1) {
            shortName = name
            pkg = null
        } else if (dollar == -1) {
            shortName = name.substring(dot + 1)
            pkg = name.substring(0, dot)
        } else {
            shortName = name.substring(dollar + 1)
            pkg = name.substring(0, dot)
        }
    }

    fun StringBuilder.appendModifier(modifiers: Int, target: Int, value: String) {
        if (modifiers and target != 0) {
            append(value).append(' ')
        }
    }

    fun indent(s: String) = "\t" + s.replace("\n", "\n\t")

    fun getType(type: Type): String {
        return when (type.sort) {
            Type.OBJECT -> {
                val name = type.className
                val dot = name.lastIndexOf('.')
                val dollar = name.indexOf('$')

                if (dot == -1) {
                    name
                } else if (dollar == -1) {
                    imports.add(name)
                    name.substring(dot + 1)
                } else {
                    imports.add(name.substring(0, dollar))
                    name.substring(dot + 1).replace('*', '.')
                }
            }
            Type.ARRAY -> getType(type.elementType) + "[]"
            else -> type.className
        }
    }

    fun decompileMethod(method: MethodNode): String {
        return buildString {
            appendModifier(method.access, Opcodes.ACC_PUBLIC, "public")
            appendModifier(method.access, Opcodes.ACC_PRIVATE, "private")
            appendModifier(method.access, Opcodes.ACC_PROTECTED, "protected")
            appendModifier(method.access, Opcodes.ACC_STATIC, "static")
            appendModifier(method.access, Opcodes.ACC_FINAL, "final")
            appendModifier(method.access, Opcodes.ACC_SYNCHRONIZED, "synchronized")
            appendModifier(method.access, Opcodes.ACC_BRIDGE, "bridge")
            appendModifier(method.access, Opcodes.ACC_VARARGS, "varargs")
            appendModifier(method.access, Opcodes.ACC_NATIVE, "native")
            appendModifier(method.access, Opcodes.ACC_ABSTRACT, "abstract")
            appendModifier(method.access, Opcodes.ACC_STRICT, "strict")
            appendModifier(method.access, Opcodes.ACC_SYNTHETIC, "synthetic")
            appendModifier(method.access, Opcodes.ACC_MANDATED, "mandated")

            if (method.name == "<init>") {
                append(shortName)
            } else {
                append(getType(Type.getReturnType(method.desc)))
                append(' ')
                append(method.name)
            }

            append('(')

            val args = Type.getArgumentTypes(method.desc)

            append(args.mapIndexed { index, arg ->
                val index = if (method.access and Opcodes.ACC_STATIC == 0) index + 1 else index

                method.localVariables?.getOrNull(index)?.let {
                    "${getType(Type.getType(it.desc))} ${it.name}"
                } ?: "${getType(arg)} var$index"
            }.joinToString())

            append(") {\n}")
        }
    }

    fun decompile(): String {
        val body = buildString {
            appendModifier(node.access, Opcodes.ACC_PUBLIC, "public")
            appendModifier(node.access, Opcodes.ACC_PRIVATE, "private")
            appendModifier(node.access, Opcodes.ACC_PROTECTED, "protected")
            appendModifier(node.access, Opcodes.ACC_FINAL, "final")
            appendModifier(node.access, Opcodes.ACC_MANDATED, "mandated")
            appendModifier(node.access, Opcodes.ACC_SYNTHETIC, "synthetic")

            if (node.access and Opcodes.ACC_ABSTRACT != 0) {
                append("abstract class")
            } else if (node.access and Opcodes.ACC_INTERFACE != 0) {
                append("interface")
            } else if (node.access and Opcodes.ACC_ANNOTATION != 0) {
                append("@interface")
            } else if (node.access and Opcodes.ACC_ENUM != 0) {
                append("enum class")
            } else if (node.access and Opcodes.ACC_MODULE != 0) {
                append("module")
            } else {
                append("class")
            }

            append(" ").append(shortName).append(" {\n")

            append(indent(node.methods.joinToString(separator = "\n\n") { decompileMethod(it) }))

            append("\n}")
        }
        val header = buildString {
            pkg?.let {
                append("package ")
                append(it)
                append(";\n\n")
            }

            if (imports.isNotEmpty()) {
                imports.filterNot { it.startsWith("java.lang") }.forEach { append("import ").append(it).append(";\n") }
                append('\n')
            }
        }
        return header + body
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val bytes = File("decompiler/build/classes/java/main/test/Test.class").readBytes()
            val node = ClassNode()
            ClassReader(bytes).accept(node, 0)
            println(BigShotDecompiler(node).decompile())
        }
    }
}