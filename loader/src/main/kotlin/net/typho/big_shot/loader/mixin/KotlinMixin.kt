package net.typho.big_shot.loader.mixin

import net.minecraft.client.Minecraft
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(Minecraft::class)
class KotlinMixin {
    @Inject(
        method = ["<init>"],
        at = [At("Head")]
    )
    fun init(ci: CallbackInfo) {
        println("kotlin works")
    }
}