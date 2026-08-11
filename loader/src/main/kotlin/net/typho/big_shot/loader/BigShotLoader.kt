package net.typho.big_shot.loader

import net.typho.asm_util.ClassTransformInfo
import net.typho.asm_util.error.ClassVisitException
import net.typho.big_shot.loader.util.EventGraph
import net.typho.big_shot.loader.util.inst.TransformEvent
import net.typho.big_shot.loader.util.inst.TransformType
import net.typho.big_shot.loader.util.mixin.KotlinMixinFixer
import org.objectweb.asm.tree.ClassNode
import java.lang.instrument.ClassFileTransformer
import java.lang.instrument.Instrumentation
import java.nio.file.Path
import java.nio.file.Paths
import java.security.ProtectionDomain
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeBytes

object BigShotLoader {
    @get:JvmName("getInstrumentation")
    lateinit var INSTRUMENTATION: Instrumentation
    @JvmField
    val TRANSFORM_EVENTS = EventGraph<String, TransformEvent>()

    @get:JvmName("getLoaderPath")
    lateinit var LOADER_PATH: Path
    @JvmField
    val DEBUG_PATH = Paths.get(".big_shot_debug")

    init {
        TRANSFORM_EVENTS.register("big_shot:kotlin_mixins") { type, info ->
            if (type == TransformType.MIXIN) {
                if (KotlinMixinFixer.fix(info.node)) {
                    info.markChanged()
                }
            }
        }
    }

    @JvmStatic
    fun debugSaveClass(
        className: String,
        bytes: ByteArray
    ) {
        val path = DEBUG_PATH.resolve("$className.class")
        path.createParentDirectories()
        path.writeBytes(bytes)
    }

    @JvmStatic
    fun onInstrumentationInit() {
        INSTRUMENTATION.addTransformer(object : ClassFileTransformer {
            override fun transform(
                loader: ClassLoader,
                className: String,
                classBeingRedefined: Class<*>?,
                protectionDomain: ProtectionDomain?,
                bytes: ByteArray
            ): ByteArray? {
                try {
                    val info = ClassTransformInfo.AgentTransform(bytes)

                    TRANSFORM_EVENTS.execute { id, event ->
                        info.fallbackErrorSource = id
                        event.transform(TransformType.CLASS, info)
                    }

                    return info.compile(::debugSaveClass)
                } catch (t: Throwable) {
                    ClassVisitException("Error while Big Shot Loader was transforming class $className", t).printStackTrace()

                    return null
                }
            }
        }, true)
    }

    @JvmStatic
    fun transformMixin(node: ClassNode) {
        val info = ClassTransformInfo.Wrapper(node)

        TRANSFORM_EVENTS.execute { id, event ->
            info.fallbackErrorSource = id
            event.transform(TransformType.MIXIN, info)
        }

        info.checkErrors()
    }

    @JvmStatic
    fun onLoaderInit() {
        println("yay loader init! $INSTRUMENTATION")
    }
}