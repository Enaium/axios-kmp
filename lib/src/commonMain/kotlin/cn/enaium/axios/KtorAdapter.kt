package cn.enaium.axios

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.Url
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.serialization.json.JsonElement
import kotlin.random.Random
import kotlin.time.TimeSource
import kotlin.coroutines.cancellation.CancellationException

/**
 * Creates the default Ktor client used by [KtorAdapter].
 */
fun defaultKtorHttpClient(configure: io.ktor.client.HttpClientConfig<*>.() -> Unit = {}): HttpClient =
    HttpClient {
        expectSuccess = false
        configure()
    }

/**
 * Default axios adapter backed by a Ktor [HttpClient].
 *
 * The adapter is engine-agnostic: the library brings an engine per platform
 * (OkHttp on JVM/Android, Darwin on Apple, Curl on Linux/MinGW, JS on
 * Kotlin/JS and Kotlin/Wasm), and `KtorAdapter()` uses Ktor's default engine
 * discovery. Pass your own client to control engine settings.
 */
class KtorAdapter private constructor(
    val httpClient: HttpClient,
    private val ownsClient: Boolean,
    @Suppress("UNUSED_PARAMETER") marker: ConstructorMarker,
) : AxiosAdapter {

    constructor() : this(defaultKtorHttpClient(), ownsClient = true, ConstructorMarker)

    constructor(httpClient: HttpClient) : this(httpClient, ownsClient = false, ConstructorMarker)

    constructor(httpClient: HttpClient, ownsClient: Boolean) : this(httpClient, ownsClient, ConstructorMarker)

    private object ConstructorMarker

    override suspend fun request(config: AxiosRequestConfig): AxiosResponse<Any?> {
        val token = config.cancelToken
        token?.throwIfRequested()

        try {
            return coroutineScope {
                val watcher = token?.let { activeToken ->
                    launch { throw activeToken.awaitCancellation() }
                }
                try {
                    executeWithTimeout(config) { perform(config) }
                } finally {
                    watcher?.cancel()
                }
            }
        } catch (error: AxiosError) {
            throw error
        } catch (error: CancellationException) {
            token?.reason?.let { throw it }
            throw error
        } catch (error: HttpRequestTimeoutException) {
            throw timeoutError(config)
        } catch (error: TimeoutCancellationException) {
            throw timeoutError(config)
        } catch (error: Throwable) {
            throw AxiosError(
                message = error.message ?: "Network Error",
                code = AxiosError.ERR_NETWORK,
                config = config,
                cause = error,
            )
        }
    }

    private suspend fun perform(config: AxiosRequestConfig): AxiosResponse<Any?> {
        val basePath = buildFullPath(
            config.baseURL,
            config.url,
            config.allowAbsoluteUrls ?: true,
        )
        val fullPath = buildURL(
            basePath,
            config.params,
            config.paramsSerializer,
            config.formSerializer ?: FormSerializerOptions(),
        )
        if (fullPath.isBlank()) {
            throw AxiosError("Invalid URL: the request URL is empty", AxiosError.ERR_INVALID_URL, config)
        }
        if (!isAbsoluteURL(fullPath)) {
            throw AxiosError("Invalid URL: $fullPath", AxiosError.ERR_INVALID_URL, config)
        }
        try {
            val parsed = Url(fullPath)
            if (parsed.host.isBlank()) {
                throw AxiosError("Invalid URL: $fullPath", AxiosError.ERR_INVALID_URL, config)
            }
            if (parsed.protocol.name != "http" && parsed.protocol.name != "https") {
                throw AxiosError(
                    "Invalid URL: unsupported protocol ${parsed.protocol.name}",
                    AxiosError.ERR_INVALID_URL,
                    config,
                )
            }
        } catch (error: AxiosError) {
            throw error
        } catch (error: Throwable) {
            throw AxiosError("Invalid URL: $fullPath", AxiosError.ERR_INVALID_URL, config, cause = error)
        }

        val method = HttpMethod.parse((config.method ?: "get").uppercase())
        val requestHeaders = AxiosHeaders.from(config.headers)

        config.auth?.let { auth ->
            if (!requestHeaders.has(HttpHeaders.Authorization)) {
                requestHeaders.setAuthorization(basicAuth(auth.username, auth.password))
            }
        }

        val body = prepareBody(config, requestHeaders)

        val response = httpClient.request(fullPath) {
            this.method = method
            expectSuccess = false

            requestHeaders.entries.forEach { (name, headerValues) ->
                if (name == "content-length" || name == "host") {
                    return@forEach
                }
                headerValues.forEach { value -> headers.append(name, value) }
            }

            if (body != null) {
                setBody(body)
            }
        }

        val bytes = readBody(config, response)
        val responseHeaders = AxiosHeaders.from(
            response.headers.entries().associate { entry -> entry.key to entry.value }
        )

        val data: Any = when (config.responseType) {
            ResponseType.Bytes, ResponseType.ArrayBuffer, ResponseType.Blob,
            ResponseType.Stream, ResponseType.FormData,
            -> bytes
            else -> bytes.decodeToString()
        }

        val axiosResponse: AxiosResponse<Any?> = AxiosResponse(
            data = data,
            status = response.status.value,
            statusText = response.status.description,
            headers = responseHeaders,
            config = config,
            request = response.call.request,
        )

        settle(axiosResponse, config)
        return axiosResponse
    }

    private suspend fun <T> executeWithTimeout(
        config: AxiosRequestConfig,
        block: suspend () -> T,
    ): T {
        val timeout = config.timeout ?: 0
        return if (timeout > 0) {
            try {
                withTimeout(timeout) { block() }
            } catch (error: TimeoutCancellationException) {
                throw timeoutError(config)
            }
        } else {
            block()
        }
    }

    private fun timeoutError(config: AxiosRequestConfig): AxiosError {
        val timeout = config.timeout ?: 0
        val clarify = config.transitional?.clarifyTimeoutError == true
        return AxiosError(
            message = config.timeoutErrorMessage ?: "timeout of ${timeout}ms exceeded",
            code = if (clarify) AxiosError.ETIMEDOUT else AxiosError.ECONNABORTED,
            config = config,
        )
    }

    private fun prepareBody(
        config: AxiosRequestConfig,
        headers: AxiosHeaders,
    ): OutgoingContent? {
        val data = config.data ?: return null
        val json = config.json ?: DefaultJson

        if (data is FormData) {
            val boundary = generateBoundary()
            val contentType = ContentType.MultiPart.FormData.withParameter("boundary", boundary)
            headers.setContentType(contentType.toString())
            val bytes = buildMultipartBody(data, boundary)
            checkBodyLength(config, bytes.size)
            return progressContent(bytes, contentType, config)
        }

        val bytes = when (data) {
            is ByteArray -> data
            is String -> data.encodeToByteArray()
            is URLSearchParams -> data.toString().encodeToByteArray()
            is JsonElement -> json.encodeToString(JsonElement.serializer(), data).encodeToByteArray()
            is Map<*, *>, is Iterable<*>, is Array<*>, is Number, is Boolean ->
                json.encodeToString(JsonElement.serializer(), data.asJsonElement(json)).encodeToByteArray()
            else -> data.toString().encodeToByteArray()
        }

        checkBodyLength(config, bytes.size)

        val contentType = headers.getContentType()?.let { raw ->
            runCatching { ContentType.parse(raw) }.getOrNull()
        } ?: when (data) {
            is String, is URLSearchParams -> ContentType.Application.FormUrlEncoded
            is JsonElement, is Map<*, *>, is Iterable<*>, is Array<*>, is Number, is Boolean ->
                ContentType.Application.Json
            else -> ContentType.Application.OctetStream
        }

        headers.setContentType(contentType.toString(), rewrite = false)
        return progressContent(bytes, contentType, config)
    }

    private fun checkBodyLength(config: AxiosRequestConfig, size: Int) {
        val max = config.maxBodyLength ?: -1
        if (max >= 0 && size.toLong() > max) {
            throw AxiosError(
                "Request body larger than maxBodyLength limit",
                AxiosError.ERR_BAD_REQUEST,
                config,
            )
        }
    }

    private fun progressContent(
        bytes: ByteArray,
        contentType: ContentType,
        config: AxiosRequestConfig,
    ): OutgoingContent {
        val listener = config.onUploadProgress
            ?: return ByteArrayContent(bytes, contentType)
        return ProgressUploadContent(bytes, contentType, listener)
    }

    private suspend fun readBody(
        config: AxiosRequestConfig,
        response: HttpResponse,
    ): ByteArray {
        val max = config.maxContentLength ?: -1
        val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        val listener = config.onDownloadProgress

        if (max >= 0 && contentLength != null && contentLength > max) {
            throw AxiosError(
                "maxContentLength size of $max exceeded",
                AxiosError.ERR_BAD_RESPONSE,
                config,
                response.call.request,
            )
        }

        if (listener == null && (max < 0 || contentLength != null)) {
            val bytes = response.bodyAsBytes()
            if (max >= 0 && bytes.size.toLong() > max) {
                throw AxiosError(
                    "maxContentLength size of $max exceeded",
                    AxiosError.ERR_BAD_RESPONSE,
                    config,
                    response.call.request,
                )
            }
            return bytes
        }

        val channel = response.bodyAsChannel()
        val chunk = ByteArray(8 * 1024)
        val buffer = Buffer()
        val reporter = listener?.let { ProgressReporter(it, contentLength, download = true) }
        var loaded = 0L

        while (true) {
            val read = channel.readAvailable(chunk)
            if (read == -1) break
            if (read == 0) continue
            loaded += read
            if (max >= 0 && loaded > max) {
                throw AxiosError(
                    "maxContentLength size of $max exceeded",
                    AxiosError.ERR_BAD_RESPONSE,
                    config,
                    response.call.request,
                )
            }
            buffer.write(chunk, 0, read)
            reporter?.report(loaded)
        }

        return buffer.readByteArray()
    }

    override fun close() {
        if (ownsClient) httpClient.close()
    }

    private companion object {
        fun generateBoundary(): String =
            "----AxiosKmpBoundary${Random.nextLong().toULong().toString(16)}"

        fun buildMultipartBody(formData: FormData, boundary: String): ByteArray {
            val chunks = mutableListOf<ByteArray>()

            fun text(value: String) {
                chunks += value.encodeToByteArray()
            }

            formData.entries().forEach { part ->
                text("--$boundary\r\n")
                text("Content-Disposition: form-data; name=\"${escape(part.name)}\"")
                part.filename?.let { filename ->
                    text("; filename=\"${escape(filename)}\"")
                }
                text("\r\n")
                part.contentType?.let { contentType ->
                    text("Content-Type: $contentType\r\n")
                }
                text("\r\n")
                chunks += part.value
                text("\r\n")
            }
            text("--$boundary--\r\n")

            val total = chunks.sumOf { it.size }
            val result = ByteArray(total)
            var offset = 0
            chunks.forEach { part ->
                part.copyInto(result, offset)
                offset += part.size
            }
            return result
        }

        fun escape(value: String): String =
            value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "")
                .replace("\n", "")
    }
}

private class ProgressUploadContent(
    private val bytes: ByteArray,
    override val contentType: ContentType,
    private val listener: (AxiosProgressEvent) -> Unit,
) : OutgoingContent.WriteChannelContent() {

    override val contentLength: Long = bytes.size.toLong()

    override suspend fun writeTo(channel: ByteWriteChannel) {
        val reporter = ProgressReporter(listener, bytes.size.toLong(), download = false)
        var sent = 0
        while (sent < bytes.size) {
            val count = minOf(8 * 1024, bytes.size - sent)
            channel.writeFully(bytes, sent, sent + count)
            sent += count
            reporter.report(sent.toLong())
        }
        channel.flush()
    }
}

private class ProgressReporter(
    private val listener: (AxiosProgressEvent) -> Unit,
    private val total: Long?,
    private val download: Boolean,
) {
    private val start = TimeSource.Monotonic.markNow()
    private var lastLoaded = 0L

    fun report(loaded: Long) {
        val elapsedMillis = start.elapsedNow().inWholeMilliseconds.coerceAtLeast(0)
        val rate = if (elapsedMillis > 0) loaded * 1000.0 / elapsedMillis else null
        val estimated = if (rate != null && rate > 0 && total != null) {
            (total - loaded).coerceAtLeast(0) / rate
        } else {
            null
        }
        val progress = if (total != null && total > 0) {
            loaded.toDouble() / total
        } else {
            null
        }
        listener(
            AxiosProgressEvent(
                loaded = loaded,
                total = total,
                progress = progress,
                bytes = loaded - lastLoaded,
                rate = rate,
                estimated = estimated,
                upload = !download,
                download = download,
                lengthComputable = total != null && total > 0,
            )
        )
        lastLoaded = loaded
    }
}
