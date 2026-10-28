package cn.enaium.axios

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

enum class ResponseType {
    Json,
    Text,
    Bytes,
    ArrayBuffer,
    Blob,
    Document,
    Stream,
    FormData,
}

data class AxiosBasicCredentials(
    val username: String,
    val password: String,
)

data class AxiosProxyConfig(
    val host: String,
    val port: Int,
    val auth: AxiosBasicCredentials? = null,
    val protocol: String = "http",
)

data class AxiosProgressEvent(
    val loaded: Long,
    val total: Long? = null,
    val progress: Double? = null,
    val bytes: Long = 0,
    val rate: Double? = null,
    val estimated: Double? = null,
    val upload: Boolean = false,
    val download: Boolean = false,
    val lengthComputable: Boolean = total != null && total > 0,
)

data class TransitionalOptions(
    var silentJSONParsing: Boolean? = null,
    var forcedJSONParsing: Boolean? = null,
    var clarifyTimeoutError: Boolean? = null,
    var legacyInterceptorReqResOrdering: Boolean? = null,
    var advertiseZstdAcceptEncoding: Boolean? = null,
    var validateStatusUndefinedResolves: Boolean? = null,
    var encodeBasicAuthUsername: Boolean? = null,
) {
    fun resolve(fallback: TransitionalOptions = DefaultTransitionalOptions): TransitionalOptions =
        TransitionalOptions(
            silentJSONParsing = silentJSONParsing ?: fallback.silentJSONParsing,
            forcedJSONParsing = forcedJSONParsing ?: fallback.forcedJSONParsing,
            clarifyTimeoutError = clarifyTimeoutError ?: fallback.clarifyTimeoutError,
            legacyInterceptorReqResOrdering =
                legacyInterceptorReqResOrdering ?: fallback.legacyInterceptorReqResOrdering,
            advertiseZstdAcceptEncoding = advertiseZstdAcceptEncoding ?: fallback.advertiseZstdAcceptEncoding,
            validateStatusUndefinedResolves =
                validateStatusUndefinedResolves ?: fallback.validateStatusUndefinedResolves,
            encodeBasicAuthUsername = encodeBasicAuthUsername ?: fallback.encodeBasicAuthUsername,
        )
}

val DefaultTransitionalOptions = TransitionalOptions(
    silentJSONParsing = true,
    forcedJSONParsing = true,
    clarifyTimeoutError = false,
    legacyInterceptorReqResOrdering = true,
    advertiseZstdAcceptEncoding = false,
    validateStatusUndefinedResolves = true,
    encodeBasicAuthUsername = false,
)

/**
 * Controls bracket notation used when params or form data are serialized.
 *
 * - [dots]: render nested keys as `a.b` instead of `a[b]`.
 * - [metaTokens]: append `[]` to array keys.
 * - [indexes]: render array items with explicit indexes.
 * - [maxDepth]: maximum nesting depth.
 */
data class FormSerializerOptions(
    val dots: Boolean = false,
    val metaTokens: Boolean = true,
    val indexes: Boolean = false,
    val maxDepth: Int = 100,
)

fun interface ParamsSerializer {
    fun serialize(params: Any?, options: FormSerializerOptions): String
}

fun interface ValidateStatus {
    fun isValid(status: Int): Boolean

    companion object {
        val Default: ValidateStatus = ValidateStatus { it in 200..299 }
        val AcceptAll: ValidateStatus = ValidateStatus { true }
    }
}

fun interface AxiosRequestTransformer {
    suspend fun transform(
        config: AxiosRequestConfig,
        data: Any?,
        headers: AxiosHeaders,
    ): Any?
}

fun interface AxiosResponseTransformer {
    suspend fun transform(
        config: AxiosRequestConfig,
        data: Any?,
        headers: AxiosHeaders,
        status: Int,
    ): Any?
}

/**
 * Pluggable transport. The default implementation is [KtorAdapter].
 */
interface AxiosAdapter {
    suspend fun request(config: AxiosRequestConfig): AxiosResponse<Any?>

    /** Releases adapter resources. The default Ktor adapter closes its client. */
    fun close() {}
}

/**
 * Per-request configuration. `null` means "inherit from the instance defaults".
 */
class AxiosRequestConfig {
    var url: String? = null
    var method: String? = null
    var baseURL: String? = null
    var allowAbsoluteUrls: Boolean? = null
    var transformRequest: List<AxiosRequestTransformer>? = null
    var transformResponse: List<AxiosResponseTransformer>? = null
    var headers: AxiosHeaders? = null
    var params: Any? = null
    var paramsSerializer: ParamsSerializer? = null
    var data: Any? = null
    var timeout: Long? = null
    var timeoutErrorMessage: String? = null
    var withCredentials: Boolean? = null
    var adapter: AxiosAdapter? = null
    var auth: AxiosBasicCredentials? = null
    var responseType: ResponseType? = null
    var responseEncoding: String? = null
    var xsrfCookieName: String? = null
    var xsrfHeaderName: String? = null
    var withXSRFToken: Boolean? = null
    var onUploadProgress: ((AxiosProgressEvent) -> Unit)? = null
    var onDownloadProgress: ((AxiosProgressEvent) -> Unit)? = null
    var maxContentLength: Long? = null
    var maxBodyLength: Long? = null
    var maxRedirects: Int? = null
    var validateStatus: ValidateStatus? = null
    var cancelToken: CancelToken? = null
    var decompress: Boolean? = null
    var transitional: TransitionalOptions? = null
    var proxy: AxiosProxyConfig? = null
    var formSerializer: FormSerializerOptions? = null
    var redact: List<String>? = null
    var sensitiveHeaders: List<String>? = null
    var json: Json? = null

    fun copy(): AxiosRequestConfig = AxiosRequestConfig().also { copy ->
        copy.url = url
        copy.method = method
        copy.baseURL = baseURL
        copy.allowAbsoluteUrls = allowAbsoluteUrls
        copy.transformRequest = transformRequest?.toList()
        copy.transformResponse = transformResponse?.toList()
        copy.headers = headers?.copy()
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
        copy.transitional = transitional?.let {
            TransitionalOptions(
                it.silentJSONParsing,
                it.forcedJSONParsing,
                it.clarifyTimeoutError,
                it.legacyInterceptorReqResOrdering,
                it.advertiseZstdAcceptEncoding,
                it.validateStatusUndefinedResolves,
                it.encodeBasicAuthUsername,
            )
        }
        copy.proxy = proxy
        copy.formSerializer = formSerializer
        copy.redact = redact?.toList()
        copy.sensitiveHeaders = sensitiveHeaders?.toList()
        copy.json = json
    }

    /** Header value for [name], or `null`. */
    fun header(name: String): String? = headers?.get(name)

    fun header(name: String, value: Any?, rewrite: Boolean = true): AxiosRequestConfig {
        (headers ?: AxiosHeaders()).also { headers = it }.set(name, value, rewrite)
        return this
    }

    fun toJSON(): Map<String, Any?> = linkedMapOf(
        "url" to url,
        "method" to method,
        "baseURL" to baseURL,
        "headers" to headers?.toJSON(),
        "params" to params,
        "data" to data,
        "timeout" to timeout,
        "responseType" to responseType?.name,
        "withCredentials" to withCredentials,
        "maxContentLength" to maxContentLength,
        "maxBodyLength" to maxBodyLength,
    )
}

data class AxiosResponse<T>(
    var data: T,
    var status: Int,
    var statusText: String,
    var headers: AxiosHeaders,
    var config: AxiosRequestConfig,
    var request: Any? = null,
    var duration: Long? = null,
) {
    fun <R> map(transform: (T) -> R): AxiosResponse<R> =
        AxiosResponse(transform(data), status, statusText, headers, config, request, duration)
}

/**
 * Decodes a JSON response body into [T] using [deserializer].
 */
fun <T> AxiosResponse<*>.decode(deserializer: DeserializationStrategy<T>): AxiosResponse<T> {
    val json = config.json ?: DefaultJson
    val decoded: T = try {
        when (val body = data) {
            is JsonElement -> json.decodeFromJsonElement(deserializer, body)
            is String -> json.decodeFromString(deserializer, body)
            is ByteArray -> json.decodeFromString(deserializer, body.decodeToString())
            else -> throw AxiosError(
                "Cannot decode response data of type ${body?.let { it::class.simpleName } ?: "null"}",
                AxiosError.ERR_BAD_RESPONSE,
                config,
                request,
            )
        }
    } catch (error: AxiosError) {
        throw error
    } catch (error: Throwable) {
        throw AxiosError(
            message = error.message ?: "Failed to decode response",
            code = AxiosError.ERR_BAD_RESPONSE,
            config = config,
            request = request,
            cause = error,
        )
    }
    return AxiosResponse(decoded, status, statusText, headers, config, request, duration)
}
