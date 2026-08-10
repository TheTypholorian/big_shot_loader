package net.typho.big_shot.loader.mixin

import net.minecraft.client.Minecraft
import net.minecraft.client.main.GameConfig
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(Minecraft::class)
class KotlinMixin {
    companion object {
        @Inject(
            method = ["<init>"],
            at = [At("HEAD")]
        )
        @JvmStatic
        private fun init(config: GameConfig, ci: CallbackInfo) {
            println("kotlin works")
        }
    }
}