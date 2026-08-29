package net.typho.big_shot.loader.shaders.test

import net.typho.big_shot.loader.shaders.bytecode.ShaderBytecodeUtils
import net.typho.big_shot.loader.shaders.reflect.JavaShaderCompiler
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import java.io.File

object ShaderBuilderTest {
    @JvmStatic
    fun main(args: Array<String>) {
        /*
        val testGlsl = """
            #version 450

            void main() {
            }
        """.trimIndent()
        val buffer = ShaderBytecodeUtils.glslToSpirV(testGlsl, ShaderType.VERTEX)
        val array = ByteArray(buffer.limit())
        buffer.get(0, array)
        File("test_spirv.bin").writeBytes(array)
         */

        /*
        val builder = ShaderBytecodeBuilder(EXEC_MODEL_VERTEX)

        builder.capabilities.add(CAP_SHADER)
        builder.import("GLSL.std.450")

        val main = ShaderFunction(ShaderBytecodeType.Function(ShaderBytecodeType.Void, listOf()), "main")
        val otherFunc = ShaderFunction(ShaderBytecodeType.Function(ShaderBytecodeType.Void, listOf()), "otherFunc")

        main.instructions.apply {
            add(ShaderInsnNode(OP_LABEL, ShaderLabelNode()))
            add(otherFunc.call(ShaderLabelNode(), listOf()))
            add(ShaderInsnNode(OP_RETURN))
        }
        otherFunc.instructions.apply {
            add(ShaderInsnNode(OP_LABEL, ShaderLabelNode()))
            add(ShaderInsnNode(OP_RETURN))
        }

        builder.functions.add(otherFunc)
        builder.functions.add(main)

        val buffer = builder.build(main)

        val array = ByteArray(buffer.limit())
        buffer.get(0, array)
        File("test_spirv_output.bin").writeBytes(array)

        println(ShaderBytecodeUtils.spirVToGlsl(buffer.asIntBuffer()))
         */

        val reader = ClassReader(File("loader/build/classes/kotlin/main/net/typho/big_shot/loader/shaders/test/TestVertexShader.class").absoluteFile.readBytes())
        val node = ClassNode()
        reader.accept(node, 0)

        val buffer = JavaShaderCompiler.compile(node)

        val array = ByteArray(buffer.limit())
        buffer.get(0, array)
        File("test_spirv_output_java.bin").writeBytes(array)

        println(ShaderBytecodeUtils.spirVToGlsl(buffer.asIntBuffer()))
    }
}