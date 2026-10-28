package cn.enaium.axios

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.serializer

/**
 * Core axios instance.
 *
 * ```kotlin
 * val api = Axios()
 * val response = api.get("https://example.com/users")
 * ```
 *
 * The default transport is [KtorAdapter]. Use
 * `Axios(adapter = MyAdapter())` to plug a different transport.
 */
class Axios(
    defaults: AxiosDefaults = AxiosDefaults(),
    private val adapter: AxiosAdapter = KtorAdapter(),
) {

    var defaults: AxiosDefaults = defaults

    val interceptors: Interceptors = Interceptors()

    val adapterOrDefault: AxiosAdapter
        get() = defaults.adapter ?: adapter

    // ---------------------------------------------------------------------
    // Requests
    // ---------------------------------------------------------------------

    suspend fun request(config: AxiosRequestConfig): AxiosResponse<Any?> {
        val merged = defaults.toRequestConfig(config)
        return execute(merged)
    }

    suspend fun request(
        url: String,
        config: AxiosRequestConfig = AxiosRequestConfig(),
    ): AxiosResponse<Any?> {
        val copy = config.copy()
        copy.url = url
        return request(copy)
    }

    suspend fun request(configure: AxiosRequestConfig.() -> Unit): AxiosResponse<Any?> =
        request(AxiosRequestConfig().apply(configure))

    suspend fun get(
        url: String,
        config: AxiosRequestConfig = AxiosRequestConfig(),
    ): AxiosResponse<Any?> = request(withMethod("get", url, config))

    suspend fun get(
        url: String,
        configure: AxiosRequestConfig.() -> Unit,
    ): AxiosResponse<Any?> = get(url, AxiosRequestConfig().apply(configure))

    suspend fun delete(
        url: String,
        config: AxiosRequestConfig = AxiosRequestConfig(),
    ): AxiosResponse<Any?> = request(withMethod("delete", url, config))

    suspend fun delete(
        url: String,
        configure: AxiosRequestConfig.() -> Unit,
    ): AxiosResponse<Any?> = delete(url, AxiosRequestConfig().apply(configure))

    suspend fun head(
        url: String,
        config: AxiosRequestConfig = AxiosRequestConfig(),
    ): AxiosResponse<Any?> = request(withMethod("head", url, config))

    suspend fun head(
        url: String,
        configure: AxiosRequestConfig.() -> Unit,
    ): AxiosResponse<Any?> = head(url, AxiosRequestConfig().apply(configure))

    suspend fun options(
        url: String,
        config: AxiosRequestConfig = AxiosRequestConfig(),
    ): AxiosResponse<Any?> = request(withMethod("options", url, config))

    suspend fun options(
        url: String,
        configure: AxiosRequestConfig.() -> Unit,
    ): AxiosResponse<Any?> = options(url, AxiosRequestConfig().apply(configure))

    suspend fun post(
        url: String,
        data: Any? = null,
        config: AxiosRequestConfig = AxiosRequestConfig(),
    ): AxiosResponse<Any?> = request(withMethod("post", url, config, data))

    suspend fun post(
        url: String,
        data: Any?,
        configure: AxiosRequestConfig.() -> Unit,
    ): AxiosResponse<Any?> = post(url, data, AxiosRequestConfig().apply(configure))

    suspend fun post(
        url: String,
        configure: AxiosRequestConfig.() -> Unit,
    ): AxiosResponse<Any?> = post(url, null, AxiosRequestConfig().apply(configure))

    suspend fun put(
        url: String,
        data: Any? = null,
        config: AxiosRequestConfig = AxiosRequestConfig(),
    ): AxiosResponse<Any?> = request(withMethod("put", url, config, data))

    suspend fun put(
        url: String,
        data: Any?,
        configure: AxiosRequestConfig.() -> Unit,
    ): AxiosResponse<Any?> = put(url, data, AxiosRequestConfig().apply(configure))

    suspend fun put(
        url: String,
        configure: AxiosRequestConfig.() -> Unit,
    ): AxiosResponse<Any?> = put(url, null, AxiosRequestConfig().apply(configure))

    suspend fun patch(
        url: String,
        data: Any? = null,
        config: AxiosRequestConfig = AxiosRequestConfig(),
    ): AxiosResponse<Any?> = request(withMethod("patch", url, config, data))

    suspend fun patch(
        url: String,
        data: Any?,
        configure: AxiosRequestConfig.() -> Unit,
    ): AxiosResponse<Any?> = patch(url, data, AxiosRequestConfig().apply(configure))

    suspend fun patch(
        url: String,
        configure: AxiosRequestConfig.() -> Unit,
    ): AxiosResponse<Any?> = patch(url, null, AxiosRequestConfig().apply(configure))

    suspend fun query(
        url: String,
        data: Any? = null,
        config: AxiosRequestConfig = AxiosRequestConfig(),
    ): AxiosResponse<Any?> = request(withMethod("query", url, config, data))

    suspend fun postForm(
        url: String,
        data: Any? = null,
        config: AxiosRequestConfig = AxiosRequestConfig(),
    ): AxiosResponse<Any?> = request(withMethod("post", url, withMultipart(config), data))

    suspend fun putForm(
        url: String,
        data: Any? = null,
        config: AxiosRequestConfig = AxiosRequestConfig(),
    ): AxiosResponse<Any?> = request(withMethod("put", url, withMultipart(config), data))

    suspend fun patchForm(
        url: String,
        data: Any? = null,
        config: AxiosRequestConfig = AxiosRequestConfig(),
    ): AxiosResponse<Any?> = request(withMethod("patch", url, withMultipart(config), data))

    // ---------------------------------------------------------------------
    // Typed requests
    // ---------------------------------------------------------------------

    suspend fun <T> request(
        config: AxiosRequestConfig,
        deserializer: DeserializationStrategy<T>,
    ): AxiosResponse<T> {
        val copy = config.copy()
        if (copy.responseType == null) copy.responseType = ResponseType.Json
        return request(copy).decode(deserializer)
    }

    suspend fun <T> get(
        url: String,
        deserializer: DeserializationStrategy<T>,
        config: AxiosRequestConfig = AxiosRequestConfig(),
    ): AxiosResponse<T> = request(withMethod("get", url, config), deserializer)

    suspend fun <T> delete(
        url: String,
        deserializer: DeserializationStrategy<T>,
        config: AxiosRequestConfig = AxiosRequestConfig(),
    ): AxiosResponse<T> = request(withMethod("delete", url, config), deserializer)

    suspend fun <T> post(
        url: String,
        data: Any?,
        deserializer: DeserializationStrategy<T>,
        config: AxiosRequestConfig = AxiosRequestConfig(),
    ): AxiosResponse<T> = request(withMethod("post", url, config, data), deserializer)

    suspend fun <T> put(
        url: String,
        data: Any?,
        deserializer: DeserializationStrategy<T>,
        config: AxiosRequestConfig = AxiosRequestConfig(),
    ): AxiosResponse<T> = request(withMethod("put", url, config, data), deserializer)

    suspend fun <T> patch(
        url: String,
        data: Any?,
        deserializer: DeserializationStrategy<T>,
        config: AxiosRequestConfig = AxiosRequestConfig(),
    ): AxiosResponse<T> = request(withMethod("patch", url, config, data), deserializer)

    // ---------------------------------------------------------------------
    // Instance management
    // ---------------------------------------------------------------------

    fun getUri(config: AxiosRequestConfig = AxiosRequestConfig()): String {
        val merged = defaults.toRequestConfig(config)
        val fullPath = buildFullPath(
            merged.baseURL,
            merged.url,
            merged.allowAbsoluteUrls ?: true,
        )
        return buildURL(
            fullPath,
            merged.params,
            merged.paramsSerializer,
            merged.formSerializer ?: FormSerializerOptions(),
        )
    }

    fun create(configure: AxiosDefaults.() -> Unit = {}): Axios {
        val copy = defaults.copy()
        copy.configure()
        return Axios(copy, defaults.adapter ?: adapter)
    }

    /**
     * Releases the adapter transport.
     *
     * Instances created through [create] share the parent adapter, so closing
     * one closes the shared `HttpClient` for all of them. Build
     * `Axios(adapter = KtorAdapter())` for an independently managed transport.
     */
    fun close() {
        (defaults.adapter ?: adapter).close()
    }

    private suspend fun execute(config: AxiosRequestConfig): AxiosResponse<Any?> {
        val requestHandlers = interceptors.request.snapshot()
            .filter { handler ->
                handler.options.runWhen?.invoke(config) ?: true
            }
            .let { handlers ->
                val legacy = config.transitional?.legacyInterceptorReqResOrdering ?: true
                if (legacy) handlers.reversed() else handlers
            }

        val responseHandlers = interceptors.response.snapshot()

        val steps = mutableListOf<ChainStep>()
        requestHandlers.forEach { steps += ChainStep(it.fulfilled, it.rejected) }
        steps += ChainStep(
            fulfilled = { value -> dispatchRequest(value as AxiosRequestConfig, adapterOrDefault) },
            rejected = null,
        )
        responseHandlers.forEach { steps += ChainStep(it.fulfilled, it.rejected) }

        var value: Any? = config
        var error: Throwable? = null

        for (step in steps) {
            if (error == null) {
                val fulfilled = step.fulfilled
                if (fulfilled != null) {
                    try {
                        value = fulfilled(value)
                    } catch (throwable: Throwable) {
                        error = throwable
                    }
                }
            } else {
                val rejected = step.rejected
                if (rejected != null) {
                    try {
                        value = rejected(error)
                        error = null
                    } catch (throwable: Throwable) {
                        error = throwable
                    }
                }
            }
        }

        error?.let { throw it }

        @Suppress("UNCHECKED_CAST")
        return value as AxiosResponse<Any?>
    }

    private fun withMethod(
        method: String,
        url: String,
        config: AxiosRequestConfig,
        data: Any? = null,
    ): AxiosRequestConfig {
        val copy = config.copy()
        copy.method = method
        copy.url = url
        if (data != null) copy.data = data
        return copy
    }

    private fun withMultipart(config: AxiosRequestConfig): AxiosRequestConfig {
        val copy = config.copy()
        copy.headers = AxiosHeaders.from(copy.headers).apply {
            setContentType("multipart/form-data", rewrite = false)
        }
        return copy
    }

    companion object {
        const val VERSION: String = "1.0.0"
    }
}

private class ChainStep(
    val fulfilled: (suspend (Any?) -> Any?)?,
    val rejected: (suspend (Throwable) -> Any?)?,
)

// -------------------------------------------------------------------------
// Reified typed helpers
// -------------------------------------------------------------------------

suspend inline fun <reified T> Axios.requestAs(
    config: AxiosRequestConfig,
): AxiosResponse<T> = request(config, serializer<T>())

suspend inline fun <reified T> Axios.getAs(
    url: String,
    config: AxiosRequestConfig = AxiosRequestConfig(),
): AxiosResponse<T> = get(url, serializer<T>(), config)

suspend inline fun <reified T> Axios.deleteAs(
    url: String,
    config: AxiosRequestConfig = AxiosRequestConfig(),
): AxiosResponse<T> = delete(url, serializer<T>(), config)

suspend inline fun <reified T> Axios.postAs(
    url: String,
    data: Any? = null,
    config: AxiosRequestConfig = AxiosRequestConfig(),
): AxiosResponse<T> = post(url, data, serializer<T>(), config)

suspend inline fun <reified T> Axios.putAs(
    url: String,
    data: Any? = null,
    config: AxiosRequestConfig = AxiosRequestConfig(),
): AxiosResponse<T> = put(url, data, serializer<T>(), config)

suspend inline fun <reified T> Axios.patchAs(
    url: String,
    data: Any? = null,
    config: AxiosRequestConfig = AxiosRequestConfig(),
): AxiosResponse<T> = patch(url, data, serializer<T>(), config)

/**
 * Shared default instance, equivalent to axios' default export.
 */
val axios: Axios by lazy { Axios() }

/** Axios KMP library version. */
const val AXIOS_VERSION: String = "1.0.0"

/**
 * Creates a new instance inheriting the shared defaults.
 */
fun create(configure: AxiosDefaults.() -> Unit = {}): Axios = axios.create(configure)
