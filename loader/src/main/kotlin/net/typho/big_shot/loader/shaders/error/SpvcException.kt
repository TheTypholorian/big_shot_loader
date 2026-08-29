package net.typho.big_shot.loader.shaders.error

import org.lwjgl.util.spvc.Spvc.*

class SpvcException : RuntimeException {
    constructor() : super()

    constructor(message: String) : super(message)

    constructor(result: Int, message: String) : super(when (result) {
        SPVC_ERROR_INVALID_SPIRV -> "SPVC_ERROR_INVALID_SPIRV"
        SPVC_ERROR_UNSUPPORTED_SPIRV -> "SPVC_ERROR_UNSUPPORTED_SPIRV"
        SPVC_ERROR_OUT_OF_MEMORY -> "SPVC_ERROR_OUT_OF_MEMORY"
        SPVC_ERROR_INVALID_ARGUMENT -> "SPVC_ERROR_INVALID_ARGUMENT"
        else -> result.toString()
    } + " ($message)")

    constructor(result: Int, context: Long, message: String) : super(when (result) {
        SPVC_ERROR_INVALID_SPIRV -> "SPVC_ERROR_INVALID_SPIRV"
        SPVC_ERROR_UNSUPPORTED_SPIRV -> "SPVC_ERROR_UNSUPPORTED_SPIRV"
        SPVC_ERROR_OUT_OF_MEMORY -> "SPVC_ERROR_OUT_OF_MEMORY"
        SPVC_ERROR_INVALID_ARGUMENT -> "SPVC_ERROR_INVALID_ARGUMENT"
        else -> result.toString()
    } + " ${spvc_context_get_last_error_string(context)} ($message)")

    constructor(message: String, cause: Throwable?) : super(message, cause)

    constructor(cause: Throwable?) : super(cause)

    constructor(message: String, cause: Throwable?, enableSuppression: Boolean, writableStackTrace: Boolean) : super(
        message,
        cause,
        enableSuppression,
        writableStackTrace
    )
}