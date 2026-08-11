package net.typho.big_shot.loader.mixin

import com.mojang.authlib.yggdrasil.ProfileResult
import net.minecraft.client.GameLoadCookie
import net.minecraft.client.Minecraft
import org.slf4j.Logger
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.gen.Accessor
import org.spongepowered.asm.mixin.gen.Invoker
import java.util.concurrent.CompletableFuture

@Mixin(Minecraft::class)
interface KotlinAccessor {
    @get:Accessor("profileFuture")
    val `big_shot$profileFuture`: CompletableFuture<ProfileResult?>

    @Invoker("onResourceLoadFinished")
    fun `big_shot$onResourceLoadFinished`(cookie: GameLoadCookie?)

    companion object {
        @get:Accessor("LOGGER")
        val `big_shot$LOGGER`: Logger
            get() = throw AssertionError()

        @Invoker("countryEqualsISO3")
        @JvmStatic
        fun `big_shot$countryEqualsISO3`(iso3Locale: Any?): Boolean {
            throw AssertionError()
        }
    }
}