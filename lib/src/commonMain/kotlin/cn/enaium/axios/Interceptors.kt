package cn.enaium.axios

/**
 * A single interceptor registration.
 *
 * [synchronous] is accepted for axios API compatibility; this port always
 * awaits interceptors because they may suspend.
 */
class AxiosInterceptorOptions(
    val synchronous: Boolean = false,
    val runWhen: ((AxiosRequestConfig) -> Boolean)? = null,
)

internal class RequestInterceptorHandler(
    val fulfilled: (suspend (Any?) -> Any?)?,
    val rejected: (suspend (Throwable) -> Any?)?,
    val options: AxiosInterceptorOptions,
)

internal class ResponseInterceptorHandler(
    val fulfilled: (suspend (Any?) -> Any?)?,
    val rejected: (suspend (Throwable) -> Any?)?,
)

/**
 * Request interceptor registry.
 *
 * Interceptors registered later run first by default, matching axios'
 * `legacyInterceptorReqResOrdering` behavior. Set
 * [TransitionalOptions.legacyInterceptorReqResOrdering] to `false` to run them
 * in registration order instead.
 *
 * Callbacks are declared last so Kotlin trailing-lambda syntax works:
 * `use { config -> config }`.
 */
class RequestInterceptorManager {

    private val handlers = mutableListOf<RequestInterceptorHandler?>()

    fun use(
        options: AxiosInterceptorOptions = AxiosInterceptorOptions(),
        onRejected: (suspend (Throwable) -> Any?)? = null,
        onFulfilled: (suspend (AxiosRequestConfig) -> AxiosRequestConfig)? = null,
    ): Int {
        val id = handlers.size
        handlers += RequestInterceptorHandler(
            fulfilled = onFulfilled?.let { interceptor ->
                { value -> interceptor(value as AxiosRequestConfig) }
            },
            rejected = onRejected,
            options = options,
        )
        return id
    }

    fun eject(id: Int) {
        if (id in handlers.indices) handlers[id] = null
    }

    fun clear() {
        handlers.clear()
    }

    internal fun snapshot(): List<RequestInterceptorHandler> = handlers.filterNotNull()
}

/**
 * Response interceptor registry.
 *
 * Interceptors run in registration order. Callbacks are declared last so Kotlin
 * trailing-lambda syntax works: `use { response -> response }`.
 */
class ResponseInterceptorManager {

    private val handlers = mutableListOf<ResponseInterceptorHandler?>()

    fun use(
        onRejected: (suspend (Throwable) -> Any?)? = null,
        onFulfilled: (suspend (AxiosResponse<Any?>) -> AxiosResponse<Any?>)? = null,
    ): Int {
        val id = handlers.size
        handlers += ResponseInterceptorHandler(
            fulfilled = onFulfilled?.let { interceptor ->
                @Suppress("UNCHECKED_CAST")
                { value -> interceptor(value as AxiosResponse<Any?>) }
            },
            rejected = onRejected,
        )
        return id
    }

    fun eject(id: Int) {
        if (id in handlers.indices) handlers[id] = null
    }

    fun clear() {
        handlers.clear()
    }

    internal fun snapshot(): List<ResponseInterceptorHandler> = handlers.filterNotNull()
}

class Interceptors internal constructor() {
    val request: RequestInterceptorManager = RequestInterceptorManager()
    val response: ResponseInterceptorManager = ResponseInterceptorManager()
}
