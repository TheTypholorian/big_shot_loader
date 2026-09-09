package net.typho.big_shot.loader.shaders.reflect

import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.MethodInsnNode

object KotlinIntrinsicsClassHandler : JavaShaderClassHandler, JavaShaderClassHandler.Supplier {
    override fun getClassHandler(className: String): JavaShaderClassHandler? {
        return if (className == "kotlin/jvm/internal/Intrinsics") this else null
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