package net.typho.big_shot.agent

import java.lang.instrument.Instrumentation

object BigShotAgent {
    @JvmStatic
    fun premain(args: String?, inst: Instrumentation) {
        inst.addTransformer { loader, className, classBeingRedefined, domain, bytes ->
            null
        }
    }
}