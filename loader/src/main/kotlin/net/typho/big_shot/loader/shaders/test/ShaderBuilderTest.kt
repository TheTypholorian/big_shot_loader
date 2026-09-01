package net.typho.big_shot.loader.shaders.test

import net.typho.big_shot.loader.shaders.ShaderType
import net.typho.big_shot.loader.shaders.bytecode.ShaderBytecodeType
import net.typho.big_shot.loader.shaders.bytecode.ShaderBytecodeUtils
import net.typho.big_shot.loader.shaders.reflect.JavaShaderCompiler
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.signature.SignatureReader
import org.objectweb.asm.tree.ClassNode
import java.io.File

object ShaderBuilderTest {
    fun compile() {
        val testGlsl = """
            #version 450

            layout(location = 0) in vec3 pos;
            layout(location = 0) out vec3 outPos;
            
            void main()
            {
                vec3 temp = vec3(0.0);
                temp = (vec3(0.0) / vec3(1.0)) * vec3(2.0);
                outPos = (temp = temp - vec3(10.0, 15.0, -2.0));
            }
        """.trimIndent()
        val buffer = ShaderBytecodeUtils.glslToSpirV(testGlsl, ShaderType.VERTEX)
        val array = ByteArray(buffer.limit())
        buffer.get(0, array)
        File("test_spirv.bin").writeBytes(array)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        compile()

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