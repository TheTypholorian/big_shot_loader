package net.typho.big_shot.loader.shaders

import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GL43.*
import org.lwjgl.util.shaderc.Shaderc.*
import org.lwjgl.vulkan.VK10.*

enum class ShaderType(
    @JvmField
    val shaderc: Int,
    @JvmField
    val opengl: Int,
    @JvmField
    val openglBit: Int,
    @JvmField
    val vulkan: Int,
    @JvmField
    val extension: String
) {
    VERTEX(shaderc_vertex_shader, GL_VERTEX_SHADER, GL_VERTEX_SHADER_BIT, VK_SHADER_STAGE_VERTEX_BIT, "vsh"),
    FRAGMENT(shaderc_fragment_shader, GL_FRAGMENT_SHADER, GL_FRAGMENT_SHADER_BIT, VK_SHADER_STAGE_FRAGMENT_BIT, "fsh"),
    COMPUTE(shaderc_compute_shader, GL_COMPUTE_SHADER, GL_COMPUTE_SHADER_BIT, VK_SHADER_STAGE_COMPUTE_BIT, "comp"),
    GEOMETRY(shaderc_geometry_shader, GL_GEOMETRY_SHADER, GL_GEOMETRY_SHADER_BIT, VK_SHADER_STAGE_GEOMETRY_BIT, "gsh"),
    TESS_CONTROL(shaderc_tess_control_shader, GL_TESS_CONTROL_SHADER, GL_TESS_CONTROL_SHADER_BIT, VK_SHADER_STAGE_TESSELLATION_CONTROL_BIT, "tcsh"),
    TESS_EVAL(shaderc_tess_evaluation_shader, GL_TESS_EVALUATION_SHADER, GL_TESS_EVALUATION_SHADER_BIT, VK_SHADER_STAGE_TESSELLATION_EVALUATION_BIT, "tcsh");

    override fun toString(): String {
        return name.lowercase()
    }
}