package cn.enaium.axios

/**
 * Per-method default headers. Mirrors the shape of `axios.defaults.headers`.
 */
class HeadersDefaults {

    val common: AxiosHeaders = AxiosHeaders(
        mapOf("Accept" to "application/json, text/plain, */*")
    )
    val delete: AxiosHeaders = AxiosHeaders()
    val get: AxiosHeaders = AxiosHeaders()
    val head: AxiosHeaders = AxiosHeaders()
    val options: AxiosHeaders = AxiosHeaders()
    val post: AxiosHeaders = AxiosHeaders()
    val put: AxiosHeaders = AxiosHeaders()
    val patch: AxiosHeaders = AxiosHeaders()
    val purge: AxiosHeaders = AxiosHeaders()
    val link: AxiosHeaders = AxiosHeaders()
    val unlink: AxiosHeaders = AxiosHeaders()
    val query: AxiosHeaders = AxiosHeaders()

    operator fun get(method: String): AxiosHeaders = when (method.lowercase()) {
        "get" -> get
        "delete" -> delete
        "head" -> head
        "options" -> options
        "post" -> post
        "put" -> put
        "patch" -> patch
        "purge" -> purge
        "link" -> link
        "unlink" -> unlink
        "query" -> query
        else -> AxiosHeaders()
    }

    fun toHeaders(method: String): AxiosHeaders = AxiosHeaders.concat(common, get(method))

    fun copy(): HeadersDefaults = HeadersDefaults().also { copy ->
        copy.common.set(common)
        copy.delete.set(delete)
        copy.get.set(get)
        copy.head.set(head)
        copy.options.set(options)
        copy.post.set(post)
        copy.put.set(put)
        copy.patch.set(patch)
        copy.purge.set(purge)
        copy.link.set(link)
        copy.unlink.set(unlink)
        copy.query.set(query)
    }
}

/**
 * Instance-wide defaults. Mutate it through `axios.defaults` or build an
 * instance with `axios.create { ... }`.
 */
class AxiosDefaults {

    var url: String? = null
    var method: String = "get"
    var baseURL: String? = null
    var allowAbsoluteUrls: Boolean = true
    var transformRequest: List<AxiosRequestTransformer> = DefaultTransforms.request
    var transformResponse: List<AxiosResponseTransformer> = DefaultTransforms.response
    var headers: HeadersDefaults = HeadersDefaults()
    var params: Any? = null
    var paramsSerializer: ParamsSerializer? = null
    var data: Any? = null
    var timeout: Long = 0
    var timeoutErrorMessage: String? = null
    var withCredentials: Boolean = false
    var adapter: AxiosAdapter? = null
    var auth: AxiosBasicCredentials? = null
    var responseType: ResponseType? = null
    var responseEncoding: String = "utf-8"
    var xsrfCookieName: String = "XSRF-TOKEN"
    var xsrfHeaderName: String = "X-XSRF-TOKEN"
    var withXSRFToken: Boolean? = null
    var onUploadProgress: ((AxiosProgressEvent) -> Unit)? = null
    var onDownloadProgress: ((AxiosProgressEvent) -> Unit)? = null
    var maxContentLength: Long = -1
    var maxBodyLength: Long = -1
    var maxRedirects: Int = 21
    var validateStatus: ValidateStatus? = ValidateStatus.Default
    var cancelToken: CancelToken? = null
    var decompress: Boolean = true
    var transitional: TransitionalOptions = DefaultTransitionalOptions
    var proxy: AxiosProxyConfig? = null
    var formSerializer: FormSerializerOptions = FormSerializerOptions()
    var redact: List<String> = emptyList()
    var sensitiveHeaders: List<String> = emptyList()
    var json: kotlinx.serialization.json.Json = DefaultJson

    fun copy(): AxiosDefaults = AxiosDefaults().also { copy ->
        copy.url = url
        copy.method = method
        copy.baseURL = baseURL
        copy.allowAbsoluteUrls = allowAbsoluteUrls
        copy.transformRequest = transformRequest.toList()
        copy.transformResponse = transformResponse.toList()
        copy.headers = headers.copy()
        copy.params = params
        copy.paramsSerializer = paramsSerializer
        copy.data = data
        copy.timeout = timeout
        copy.timeoutErrorMessage = timeoutErrorMessage
        copy.withCredentials = withCredentials
        copy.adapter = adapter
        copy.auth = auth
        copy.responseType = responseType
        copy.responseEncoding = responseEncoding
        copy.xsrfCookieName = xsrfCookieName
        copy.xsrfHeaderName = xsrfHeaderName
        copy.withXSRFToken = withXSRFToken
        copy.onUploadProgress = onUploadProgress
        copy.onDownloadProgress = onDownloadProgress
        copy.maxContentLength = maxContentLength
        copy.maxBodyLength = maxBodyLength
        copy.maxRedirects = maxRedirects
        copy.validateStatus = validateStatus
        copy.cancelToken = cancelToken
        copy.decompress = decompress
        copy.transitional = transitional.resolve()
        copy.proxy = proxy
        copy.formSerializer = formSerializer
        copy.redact = redact.toList()
        copy.sensitiveHeaders = sensitiveHeaders.toList()
        copy.json = json
    }

    fun toConfig(): AxiosRequestConfig = AxiosRequestConfig().also { config ->
        config.url = url
        config.method = method
        config.baseURL = baseURL
        config.allowAbsoluteUrls = allowAbsoluteUrls
        config.transformRequest = transformRequest
        config.transformResponse = transformResponse
        config.headers = null
        config.params = params
        config.paramsSerializer = paramsSerializer
        config.data = data
        config.timeout = timeout
        config.timeoutErrorMessage = timeoutErrorMessage
        config.withCredentials = withCredentials
        config.adapter = adapter
        config.auth = auth
        config.responseType = responseType
        config.responseEncoding = responseEncoding
        config.xsrfCookieName = xsrfCookieName
        config.xsrfHeaderName = xsrfHeaderName
        config.withXSRFToken = withXSRFToken
        config.onUploadProgress = onUploadProgress
        config.onDownloadProgress = onDownloadProgress
        config.maxContentLength = maxContentLength
        config.maxBodyLength = maxBodyLength
        config.maxRedirects = maxRedirects
        config.validateStatus = validateStatus
        config.cancelToken = cancelToken
        config.decompress = decompress
        config.transitional = transitional
        config.proxy = proxy
        config.formSerializer = formSerializer
        config.redact = redact
        config.sensitiveHeaders = sensitiveHeaders
        config.json = json
    }

    internal fun toRequestConfig(request: AxiosRequestConfig): AxiosRequestConfig {
        val merged = mergeConfig(toConfig(), request)
        val method = (merged.method ?: "get").lowercase()
        merged.method = method
        merged.headers = AxiosHeaders.concat(headers.common, headers[method], request.headers)
        merged.transitional = when (val requestTransitional = request.transitional) {
            null -> transitional.resolve()
            else -> requestTransitional.resolve(transitional.resolve())
        }
        merged.json = request.json ?: json
        return merged
    }
}

object DefaultTransforms {

    val request: List<AxiosRequestTransformer> = listOf(
        AxiosRequestTransformer { config, data, headers ->
            defaultTransformRequest(config, data, headers)
        }
    )

    val response: List<AxiosResponseTransformer> = listOf(
        AxiosResponseTransformer { config, data, _, _ ->
            defaultTransformResponse(config, data)
        }
    )

    private fun defaultTransformRequest(
        config: AxiosRequestConfig,
        data: Any?,
        headers: AxiosHeaders,
    ): Any? {
        if (data == null) return null
        val json = config.json ?: DefaultJson
        val contentType = headers.getContentType()?.lowercase().orEmpty()
        val hasJsonContentType = contentType.contains("application/json")
        val formOptions = config.formSerializer ?: FormSerializerOptions()

        if (data is FormData) {
            return if (hasJsonContentType) {
                json.encodeToString(
                    kotlinx.serialization.json.JsonElement.serializer(),
                    data.toJSON().asJsonElement(json),
                )
            } else {
                data
            }
        }

        if (data is ByteArray) return data

        if (data is URLSearchParams) {
            headers.setContentType("application/x-www-form-urlencoded;charset=utf-8", false)
            return data.toString()
        }

        if (data is String) {
            if (hasJsonContentType) {
                return try {
                    json.parseToJsonElement(data)
                    data.trim()
                } catch (_: Throwable) {
                    json.encodeToString(
                        kotlinx.serialization.json.JsonElement.serializer(),
                        kotlinx.serialization.json.JsonPrimitive(data),
                    )
                }
            }
            return data
        }

        if (data is kotlinx.serialization.json.JsonElement) {
            headers.setContentType("application/json", false)
            return json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), data)
        }

        val isStructured = data is Map<*, *> || data is Iterable<*> || data is Array<*> ||
            data is Number || data is Boolean

        if (isStructured) {
            if (contentType.contains("application/x-www-form-urlencoded")) {
                return toURLEncodedForm(data, formOptions).toString()
            }
            if (contentType.contains("multipart/form-data")) {
                return toFormData(data, options = formOptions)
            }
            headers.setContentType("application/json", false)
            return json.encodeToString(
                kotlinx.serialization.json.JsonElement.serializer(),
                data.asJsonElement(json),
            )
        }

        return data
    }

    private fun defaultTransformResponse(config: AxiosRequestConfig, data: Any?): Any? {
        val json = config.json ?: DefaultJson
        val transitional = config.transitional ?: DefaultTransitionalOptions
        var value = data

        if (value is ByteArray) {
            value = when (config.responseType) {
                ResponseType.Bytes, ResponseType.ArrayBuffer, ResponseType.Blob,
                ResponseType.Stream, ResponseType.FormData,
                -> return value
                else -> value.decodeToString()
            }
        }

        if (value is String) {
            val responseType = config.responseType
            val wantsJson = responseType == ResponseType.Json ||
                (responseType == null && transitional.forcedJSONParsing != false)

            if (wantsJson && value.isNotEmpty()) {
                if (!looksLikeJson(value)) {
                    if (responseType == ResponseType.Json && transitional.silentJSONParsing == false) {
                        throw AxiosError(
                            "Unexpected token in JSON: ${value.take(20)}",
                            AxiosError.ERR_BAD_RESPONSE,
                            config,
                        )
                    }
                } else {
                    try {
                        return json.parseToJsonElement(value)
                    } catch (error: Throwable) {
                        if (responseType == ResponseType.Json && transitional.silentJSONParsing == false) {
                            throw AxiosError.from(error, AxiosError.ERR_BAD_RESPONSE, config)
                        }
                    }
                }
            }
        }

        return value
    }
}

private fun looksLikeJson(value: String): Boolean {
    val trimmed = value.trim()
    if (trimmed == "true" || trimmed == "false" || trimmed == "null") return true
    val first = trimmed.firstOrNull() ?: return false
    return first == '{' || first == '[' || first == '"' || first == '-' || first in '0'..'9'
}

internal fun mergeParams(first: Any?, second: Any?): Any? {
    if (second == null) return first
    if (first == null) return second
    if (first is Map<*, *> && second is Map<*, *>) {
        val result = LinkedHashMap<Any?, Any?>()
        first.forEach { (key, value) -> result[key] = value }
        second.forEach { (key, value) -> result[key] = value }
        return result
    }
    return second
}
