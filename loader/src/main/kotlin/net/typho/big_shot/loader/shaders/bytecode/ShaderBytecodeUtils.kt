package net.typho.big_shot.loader.shaders.bytecode

import com.sun.tools.javac.code.Lint.LintCategory.options
import net.typho.big_shot.loader.shaders.ShaderType
import net.typho.big_shot.loader.shaders.error.ShadercException
import net.typho.big_shot.loader.shaders.error.SpvcException
import org.lwjgl.system.MemoryStack
import org.lwjgl.util.shaderc.Shaderc.*
import org.lwjgl.util.spvc.Spvc.*
import java.nio.ByteBuffer
import java.nio.IntBuffer
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.use

object ShaderBytecodeUtils {
    @JvmStatic
    fun spvcRun(result: Int, message: String) {
        if (result != SPVC_SUCCESS) {
            throw SpvcException(result, message)
        }
    }

    @JvmStatic
    fun spvcRun(context: Long, result: Int, message: String) {
        if (result != SPVC_SUCCESS) {
            throw SpvcException(result, context, message)
        }
    }

    @JvmStatic
    fun shadercRun(result: Long, message: String): Long {
        if (result == 0L) {
            throw ShadercException(message)
        }

        return result
    }

    @JvmStatic
    fun shadercRunCompile(result: Long, message: String): Long {
        shadercRun(result, message)

        val status = shaderc_result_get_compilation_status(result)

        if (status != shaderc_compilation_status_success) {
            throw ShadercException(status, result, message)
        }

        return result
    }

    @OptIn(ExperimentalContracts::class)
    @JvmStatic
    fun <R> useSpvcContext(out: (stack: MemoryStack, context: Long) -> R): R {
        contract {
            callsInPlace(out, InvocationKind.AT_MOST_ONCE)
        }

        MemoryStack.stackPush().use { stack ->
            val pointer = stack.callocPointer(1)
            spvcRun(spvc_context_create(pointer), "Creating spvc context")
            val context = pointer.get(0)

            try {
                return out(stack, context)
            } finally {
                spvc_context_destroy(context)
            }
        }
    }

    @OptIn(ExperimentalContracts::class)
    @JvmStatic
    fun <R> useShadercCompiler(out: (compiler: Long) -> R): R {
        contract {
            callsInPlace(out, InvocationKind.AT_MOST_ONCE)
        }

        val compiler = shadercRun(shaderc_compiler_initialize(), "Creating shaderc context")

        try {
            return out(compiler)
        } finally {
            shaderc_compiler_release(compiler)
        }
    }

    @JvmOverloads
    @JvmStatic
    fun glslToSpirV(glsl: String, type: ShaderType, fileName: String? = null, entrypointMethod: String = "main", options: Long = 0L): ByteBuffer {
        return useShadercCompiler { compiler ->
            shaderc_result_get_bytes(
                shadercRunCompile(
                    shaderc_compile_into_spv(
                        compiler,
                        glsl,
                        type.shaderc,
                        fileName ?: "",
                        entrypointMethod,
                        options
                    ),
                    "Compiling $type shader $fileName"
                )
            )!!
        }
    }

    @JvmStatic
    fun spirVToGlsl(spirv: IntBuffer): String {
        return useSpvcContext { stack, context ->
            val pointer = stack.mallocPointer(1)

            spvcRun(context, spvc_context_parse_spirv(context, spirv, spirv.remaining().toLong(), pointer), "Parsing spir-v")
            val parsed = pointer.get(0)

            spvcRun(context, spvc_context_create_compiler(context, SPVC_BACKEND_GLSL, parsed, SPVC_CAPTURE_MODE_TAKE_OWNERSHIP, pointer), "Creating spvc compiler")
            val compiler = pointer.get(0)

            spvcRun(context, spvc_compiler_compile(compiler, pointer), "Compiling spir-v to glsl")
            pointer.getStringUTF8(0)
        }
    }
}