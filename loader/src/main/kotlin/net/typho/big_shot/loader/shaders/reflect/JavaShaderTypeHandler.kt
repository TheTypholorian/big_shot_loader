package net.typho.big_shot.loader.shaders.reflect

import net.typho.big_shot.loader.shaders.bytecode.ShaderBytecodeType
import net.typho.big_shot.loader.shaders.bytecode.ShaderVariable
import org.objectweb.asm.Type
import org.objectweb.asm.tree.LocalVariableNode
import org.objectweb.asm.tree.MethodInsnNode

interface JavaShaderTypeHandler {
    fun handleMethodCall(compiler: JavaShaderMethodCompiler, call: MethodInsnNode)

    fun createLocalVariable(compiler: JavaShaderMethodCompiler, local: LocalVariableNode, javaType: Type, type: ShaderBytecodeType): ShaderVariable? {
        return null
    }

    interface Supplier {
        fun getTypeHandler(type: Type): JavaShaderTypeHandler?
    }
}