package net.typho.big_shot.loader.mixin

import net.minecraft.client.Minecraft
import net.minecraft.client.main.GameConfig
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(Minecraft::class)
class KotlinMixin {
    @Inject(
        method = ["<init>"],
        at = [At("TAIL")]
    )
    private fun initTail(config: GameConfig, ci: CallbackInfo) {
        println("kotlin TAIL works")

        println((this as KotlinAccessor).`big_shot$profileFuture`)
        println((this as KotlinAccessor).`big_shot$onResourceLoadFinished`(null))
        println(KotlinAccessor.`big_shot$LOGGER`)
        println(KotlinAccessor.`big_shot$countryEqualsISO3`(null))
    }

    companion object {
        @Inject(
            method = ["<init>"],
            at = [At("HEAD")]
        )
        @JvmStatic
        private fun initHead(config: GameConfig, ci: CallbackInfo) {
            println("kotlin HEAD works")
        }
    }
}