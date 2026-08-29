package net.typho.big_shot.loader.shaders.error

import org.lwjgl.util.shaderc.Shaderc.*

class ShadercException : RuntimeException {
    constructor() : super()

    constructor(message: String) : super(message)

    constructor(status: Int, result: Long, message: String) : super(when (status) {
        shaderc_compilation_status_invalid_stage -> "shaderc_compilation_status_invalid_stage"
        shaderc_compilation_status_compilation_error -> "shaderc_compilation_status_compilation_error"
        shaderc_compilation_status_internal_error -> "shaderc_compilation_status_internal_error"
        shaderc_compilation_status_null_result_object -> "shaderc_compilation_status_null_result_object"
        shaderc_compilation_status_invalid_assembly -> "shaderc_compilation_status_invalid_assembly"
        shaderc_compilation_status_validation_error -> "shaderc_compilation_status_validation_error"
        shaderc_compilation_status_transformation_error -> "shaderc_compilation_status_transformation_error"
        shaderc_compilation_status_configuration_error -> "shaderc_compilation_status_configuration_error"
        else -> status.toString()
    } + " ${shaderc_result_get_error_message(result)} ($message)")

    constructor(message: String, cause: Throwable?) : super(message, cause)

    constructor(cause: Throwable?) : super(cause)

    constructor(message: String, cause: Throwable?, enableSuppression: Boolean, writableStackTrace: Boolean) : super(
        message,
        cause,
        enableSuppression,
        writableStackTrace
    )
}