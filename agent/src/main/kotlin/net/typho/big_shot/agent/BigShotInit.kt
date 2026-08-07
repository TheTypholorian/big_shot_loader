package net.typho.big_shot.agent

import org.spongepowered.asm.mixin.Mixins
import java.lang.instrument.Instrumentation

object BigShotInit {
    @get:JvmName("getInstrumentation")
    lateinit var INSTRUMENTATION: Instrumentation

    @JvmStatic
    fun init() {
        println("hey init $INSTRUMENTATION")
    }
}