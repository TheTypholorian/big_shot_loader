package net.typho.big_shot.loader.shaders.reflect

import org.objectweb.asm.Type
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.MethodInsnNode

interface JavaShaderTypeHandler {
    fun handleFieldOp(compiler: JavaShaderMethodCompiler, op: FieldInsnNode)

    fun handleMethodCall(compiler: JavaShaderMethodCompiler, call: MethodInsnNode)

    fun handleCastFrom(compiler: JavaShaderMethodCompiler, from: JavaShaderMethodCompiler.StackValue)

    interface Supplier {
        fun getTypeHandler(type: Type): JavaShaderTypeHandler?
    }
}