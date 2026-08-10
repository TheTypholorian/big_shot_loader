package net.typho.big_shot.loader.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class JavaMixin {
    @Inject(
            method = "<init>",
            at = @At("HEAD")
    )
    private static void init(GameConfig gameConfig, CallbackInfo ci) {
        System.out.println("java works");
    }
}
