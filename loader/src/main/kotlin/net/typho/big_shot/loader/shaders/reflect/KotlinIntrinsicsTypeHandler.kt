package net.typho.big_shot.loader.shaders.reflect

import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.MethodInsnNode

object KotlinIntrinsicsTypeHandler : JavaShaderTypeHandler, JavaShaderTypeHandler.Supplier {
    override fun getTypeHandler(type: Type): JavaShaderTypeHandler? {
        return if (type.internalName == "kotlin/jvm/internal/Intrinsics") this else null
    }

    override fun handleFieldOp(
        compiler: JavaShaderMethodCompiler,
        op: FieldInsnNode
    ) {
    }

    override fun handleMethodCall(
        compiler: JavaShaderMethodCompiler,
        call: MethodInsnNode
    ) {
        repeat(Type.getArgumentCount(call.desc)) {
            compiler.stack.pop()
        }

        if (call.opcode != Opcodes.INVOKESTATIC) {
            compiler.stack.pop()
        }
    }
}