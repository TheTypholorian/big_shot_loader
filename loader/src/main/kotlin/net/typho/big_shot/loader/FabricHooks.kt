package net.typho.big_shot.loader

import net.fabricmc.loader.impl.game.GameProvider

object FabricHooks {
    @JvmStatic
    fun clinit() {
        println("loaded into a bright future with mucho shenanigans to come")
    }

    @JvmStatic
    fun loadGameProvider(provider: GameProvider) {
        println("game provider $provider, name ${provider.gameName} and version ${provider.rawGameVersion}")
    }
}