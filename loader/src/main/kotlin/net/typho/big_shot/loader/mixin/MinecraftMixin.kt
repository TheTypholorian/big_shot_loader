package net.typho.big_shot.loader.mixin

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import net.minecraft.client.Minecraft
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At

@Mixin(Minecraft::class)
class MinecraftMixin {
    @WrapOperation(
        method = ["createTitle"],
        at = [At(
            value = "INVOKE",
            target = "Ljava/lang/StringBuilder;toString()Ljava/lang/String;"
        )]
    )
    private fun createTitle(instance: StringBuilder, operation: Operation<String>): String {
        return operation.call(instance.append(" + Big Shot Loader"))
    }
}