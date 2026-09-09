package net.typho.big_shot.loader.shaders.reflect

import org.objectweb.asm.tree.MethodInsnNode

interface JavaShaderClassHandler {
    fun handleMethodCall(compiler: JavaShaderMethodCompiler, call: MethodInsnNode)

    interface Supplier {
        fun getClassHandler(className: String): JavaShaderClassHandler?
    }
}