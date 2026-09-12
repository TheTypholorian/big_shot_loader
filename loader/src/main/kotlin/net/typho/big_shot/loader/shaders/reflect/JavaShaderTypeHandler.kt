package net.typho.big_shot.loader.shaders.reflect

import net.typho.big_shot.loader.shaders.bytecode.ShaderBytecodeType
import net.typho.big_shot.loader.shaders.bytecode.ShaderVariable
import org.objectweb.asm.Type
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.MethodInsnNode

interface JavaShaderTypeHandler {
    fun handleFieldOp(compiler: JavaShaderMethodCompiler, op: FieldInsnNode)

    fun handleMethodCall(compiler: JavaShaderMethodCompiler, call: MethodInsnNode)

    fun handleCastFrom(compiler: JavaShaderMethodCompiler, from: JavaShaderMethodCompiler.StackValue)

    fun createLocalVariable(compiler: JavaShaderMethodCompiler, javaType: Type, type: ShaderBytecodeType): ShaderVariable? {
        return null
    }

    interface Supplier {
        fun getTypeHandler(type: Type): JavaShaderTypeHandler?
    }
}