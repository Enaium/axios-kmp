package cn.enaium.axios

import kotlin.time.TimeSource

internal fun throwIfCancellationRequested(config: AxiosRequestConfig) {
    config.cancelToken?.throwIfRequested()
}

internal suspend fun transformData(
    config: AxiosRequestConfig,
    transformers: List<AxiosResponseTransformer>,
    data: Any?,
    headers: AxiosHeaders,
    status: Int,
): Any? {
    var value = data
    transformers.forEach { transformer ->
        value = transformer.transform(config, value, headers, status)
    }
    return value
}

internal suspend fun transformRequestData(
    config: AxiosRequestConfig,
    transformers: List<AxiosRequestTransformer>,
    data: Any?,
    headers: AxiosHeaders,
): Any? {
    var value = data
    transformers.forEach { transformer ->
        value = transformer.transform(config, value, headers)
    }
    return value
}

/**
 * Resolves or rejects a response based on [AxiosRequestConfig.validateStatus].
 */
internal fun settle(response: AxiosResponse<Any?>, config: AxiosRequestConfig) {
    val validateStatus = config.validateStatus
    if (response.status == 0 || validateStatus == null || validateStatus.isValid(response.status)) {
        return
    }
    throw AxiosError(
        "Request failed with status code ${response.status}",
        if (response.status in 400..499) AxiosError.ERR_BAD_REQUEST else AxiosError.ERR_BAD_RESPONSE,
        config,
        response.request,
        response,
    )
}

private val formMethods = setOf("post", "put", "patch")

/**
 * Runs request transforms, dispatches through the adapter and runs response
 * transforms.
 */
internal suspend fun dispatchRequest(
    config: AxiosRequestConfig,
    fallbackAdapter: AxiosAdapter,
): AxiosResponse<Any?> {
    throwIfCancellationRequested(config)

    config.headers = AxiosHeaders.from(config.headers)
    config.data = transformRequestData(
        config,
        config.transformRequest ?: DefaultTransforms.request,
        config.data,
        config.headers!!,
    )

    if ((config.method?.lowercase() ?: "") in formMethods) {
        config.headers?.setContentType("application/x-www-form-urlencoded", false)
    }

    val adapter = config.adapter ?: fallbackAdapter
    val start = TimeSource.Monotonic.markNow()

    try {
        val response = adapter.request(config)
        throwIfCancellationRequested(config)

        response.duration = start.elapsedNow().inWholeMilliseconds.coerceAtLeast(0)
        response.config = config
        response.headers = AxiosHeaders.from(response.headers)
        response.data = transformData(
            config,
            config.transformResponse ?: DefaultTransforms.response,
            response.data,
            response.headers,
            response.status,
        )
        return response
    } catch (error: Throwable) {
        if (error is CanceledError) throw error
        throwIfCancellationRequested(config)

        if (error is AxiosError && error.response != null) {
            val response = error.response!!
            response.duration = start.elapsedNow().inWholeMilliseconds.coerceAtLeast(0)
            response.config = config
            response.headers = AxiosHeaders.from(response.headers)
            response.data = transformData(
                config,
                config.transformResponse ?: DefaultTransforms.response,
                response.data,
                response.headers,
                response.status,
            )
            error.status = response.status
        }
        throw error
    }
}
