package cn.enaium.axios

/**
 * The error type thrown by every axios operation.
 *
 * Mirrors axios' `AxiosError`: [code] carries one of the
 * [AxiosError.ERR_BAD_REQUEST] style constants, and [response] is present for
 * HTTP status failures.
 */
open class AxiosError(
    message: String? = null,
    val code: String? = null,
    var config: AxiosRequestConfig? = null,
    var request: Any? = null,
    var response: AxiosResponse<Any?>? = null,
    cause: Throwable? = null,
) : Exception(message ?: code ?: "AxiosError", cause) {

    val isAxiosError: Boolean = true

    var status: Int? = response?.status

    fun toJSON(): Map<String, Any?> = linkedMapOf(
        "message" to message,
        "name" to "AxiosError",
        "config" to config?.toJSON()?.let { redact(it) },
        "code" to code,
        "status" to status,
    )

    private fun redact(value: Any?): Any? {
        val keys = config?.redact.orEmpty().map { it.lowercase() }.toSet()
        if (keys.isEmpty()) return value
        return redactValue(value, keys)
    }

    private fun redactValue(value: Any?, keys: Set<String>): Any? = when (value) {
        is Map<*, *> -> value.entries.associate { (key, item) ->
            val name = key?.toString().orEmpty()
            if (name.lowercase() in keys) {
                name to "[REDACTED ****]"
            } else {
                name to redactValue(item, keys)
            }
        }
        is Iterable<*> -> value.map { redactValue(it, keys) }
        else -> value
    }

    companion object {
        const val ERR_BAD_OPTION_VALUE = "ERR_BAD_OPTION_VALUE"
        const val ERR_BAD_OPTION = "ERR_BAD_OPTION"
        const val ECONNABORTED = "ECONNABORTED"
        const val ETIMEDOUT = "ETIMEDOUT"
        const val ECONNREFUSED = "ECONNREFUSED"
        const val ERR_NETWORK = "ERR_NETWORK"
        const val ERR_FR_TOO_MANY_REDIRECTS = "ERR_FR_TOO_MANY_REDIRECTS"
        const val ERR_DEPRECATED = "ERR_DEPRECATED"
        const val ERR_BAD_RESPONSE = "ERR_BAD_RESPONSE"
        const val ERR_BAD_REQUEST = "ERR_BAD_REQUEST"
        const val ERR_CANCELED = "ERR_CANCELED"
        const val ERR_NOT_SUPPORT = "ERR_NOT_SUPPORT"
        const val ERR_INVALID_URL = "ERR_INVALID_URL"
        const val ERR_FORM_DATA_DEPTH_EXCEEDED = "ERR_FORM_DATA_DEPTH_EXCEEDED"

        fun from(
            error: Throwable,
            code: String? = null,
            config: AxiosRequestConfig? = null,
            request: Any? = null,
            response: AxiosResponse<Any?>? = null,
        ): AxiosError = AxiosError(
            message = error.message,
            code = code,
            config = config,
            request = request,
            response = response,
            cause = error,
        )
    }
}

fun isAxiosError(value: Any?): Boolean = value is AxiosError

/**
 * Thrown when an operation is canceled through a [CancelToken].
 */
class CanceledError(
    message: String? = null,
    config: AxiosRequestConfig? = null,
    request: Any? = null,
) : AxiosError(
    message = message ?: "canceled",
    code = AxiosError.ERR_CANCELED,
    config = config,
    request = request,
) {
    val isCancel: Boolean = true
}

fun isCancel(value: Any?): Boolean = value is CanceledError
