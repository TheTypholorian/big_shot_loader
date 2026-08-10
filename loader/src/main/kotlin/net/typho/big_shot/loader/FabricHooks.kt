package net.typho.big_shot.loader

import net.fabricmc.loader.impl.game.GameProvider
import net.fabricmc.loader.impl.launch.FabricLauncherBase
import org.spongepowered.asm.mixin.Mixins
import java.nio.file.Path

object FabricHooks {
    @get:JvmName("getLoaderPath")
    lateinit var LOADER_PATH: Path

    @JvmStatic
    fun clinit() {
        println("loaded into a bright future with mucho shenanigans to come")
    }

    @JvmStatic
    fun loadGameProvider(provider: GameProvider) {
        println("game provider $provider, name ${provider.gameName} and version ${provider.rawGameVersion}")
    }

    @JvmStatic
    fun registerMixins() {
        println("registering mixins")
        Mixins.addConfiguration("big_shot.loader.mixins.json")
    }

    @JvmStatic
    fun finishModLoading() {
        FabricLauncherBase.getLauncher().addToClassPath(LOADER_PATH)
    }
}