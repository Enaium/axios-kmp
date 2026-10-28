package cn.enaium.axios.sample

import cn.enaium.axios.Axios
import cn.enaium.axios.AxiosAdapter
import cn.enaium.axios.AxiosError
import cn.enaium.axios.AxiosHeaders
import cn.enaium.axios.AxiosRequestConfig
import cn.enaium.axios.AxiosResponse
import cn.enaium.axios.CancelToken
import cn.enaium.axios.FormData
import cn.enaium.axios.ResponseType
import cn.enaium.axios.create
import cn.enaium.axios.getAs
import cn.enaium.axios.isCancel
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

@Serializable
data class SampleUser(
    val id: Int,
    val name: String,
    val username: String,
    val email: String,
)

data class DemoResult(
    val title: String,
    val status: Int? = null,
    val statusText: String? = null,
    val durationMs: Long? = null,
    val body: String = "",
    val errorCode: String? = null,
    val errorMessage: String? = null,
)

data class DemoAction(
    val label: String,
    val description: String,
    val run: suspend () -> DemoResult,
)

/**
 * Runnable examples exercising the library against public test APIs.
 */
class AxiosPlayground {

    private val pretty = Json { prettyPrint = true; isLenient = true }

    private val client: Axios = create {
        baseURL = "https://httpbin.org"
        timeout = 20_000
        headers.common["X-Client"] = "axios-kmp"
    }.also { instance ->
        instance.interceptors.request.use { config ->
            config.copy().apply {
                headers = AxiosHeaders.from(headers).apply {
                    set("X-Request-Interceptor", "active")
                }
            }
        }
        instance.interceptors.response.use { response ->
            response.copy(
                headers = response.headers.copy().apply {
                    set("X-Intercepted-By", "axios-kmp")
                }
            )
        }
    }

    private val customAdapterClient: Axios = Axios(
        adapter = object : AxiosAdapter {
            override suspend fun request(config: AxiosRequestConfig): AxiosResponse<Any?> =
                AxiosResponse(
                    data = """{"adapter":"custom","message":"No network was used"}""",
                    status = 200,
                    statusText = "OK",
                    headers = AxiosHeaders(mapOf("X-Adapter" to "custom")),
                    config = config,
                )
        }
    )

    val actions: List<DemoAction> = listOf(
        DemoAction(
            label = "GET",
            description = "GET /get with nested params and interceptors",
        ) {
            demo("GET /get") {
                val response = client.get(
                    "/get",
                    AxiosRequestConfig().apply {
                        params = mapOf(
                            "framework" to "kotlin",
                            "query" to "axios port",
                            "platforms" to listOf("jvm", "js", "wasm", "ios", "android"),
                        )
                    },
                )
                response.toResult("GET /get")
            }
        },
        DemoAction(
            label = "POST JSON",
            description = "POST /post with a JSON map body",
        ) {
            demo("POST /post") {
                val response = client.post(
                    "/post",
                    mapOf(
                        "name" to "Axios KMP",
                        "source" to "axios 1.21.0",
                        "typed" to false,
                    ),
                )
                response.toResult("POST /post")
            }
        },
        DemoAction(
            label = "Typed",
            description = "Deserialize JSONPlaceholder into a @Serializable class",
        ) {
            demo("Typed response") {
                val response = client.getAs<SampleUser>("https://jsonplaceholder.typicode.com/users/1")
                DemoResult(
                    title = "Typed response",
                    status = response.status,
                    statusText = response.statusText,
                    durationMs = response.duration,
                    body = pretty.encodeToString(
                        SampleUser.serializer(),
                        response.data,
                    ),
                )
            }
        },
        DemoAction(
            label = "Multipart",
            description = "Upload a form field and a file part",
        ) {
            demo("Multipart upload") {
                val form = FormData().apply {
                    append("title", "Axios KMP")
                    append(
                        name = "file",
                        value = "hello from Kotlin Multiplatform".encodeToByteArray(),
                        filename = "hello.txt",
                        contentType = "text/plain",
                    )
                }
                val response = client.post("/post", form)
                response.toResult("Multipart upload")
            }
        },
        DemoAction(
            label = "404",
            description = "Rejected status mapped to AxiosError.ERR_BAD_REQUEST",
        ) {
            demo("404 handling") {
                val response = client.get("/status/404")
                response.toResult("404 handling")
            }
        },
        DemoAction(
            label = "Timeout",
            description = "500 ms timeout against httpbin /delay/5",
        ) {
            demo("Timeout") {
                val response = client.get(
                    "/delay/5",
                    AxiosRequestConfig().apply { timeout = 500 },
                )
                response.toResult("Timeout")
            }
        },
        DemoAction(
            label = "Cancel",
            description = "CancelToken cancellation before dispatch",
        ) {
            demo("Cancellation") {
                val source = CancelToken.source()
                source.cancel("sample cancellation")
                val response = client.get(
                    "/get",
                    AxiosRequestConfig().apply { cancelToken = source.token },
                )
                response.toResult("Cancellation")
            }
        },
        DemoAction(
            label = "Bytes",
            description = "responseType = Bytes returns a ByteArray",
        ) {
            demo("Byte response") {
                val response = client.get(
                    "/bytes/32",
                    AxiosRequestConfig().apply { responseType = ResponseType.Bytes },
                )
                val bytes = response.data as ByteArray
                DemoResult(
                    title = "Byte response",
                    status = response.status,
                    statusText = response.statusText,
                    durationMs = response.duration,
                    body = "${bytes.size} bytes received\n" +
                        bytes.joinToString(" ") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') },
                )
            }
        },
        DemoAction(
            label = "Custom adapter",
            description = "Plug a non-Ktor adapter into Axios",
        ) {
            demo("Custom adapter") {
                val response = customAdapterClient.get("https://example.invalid/custom")
                response.toResult("Custom adapter")
            }
        },
    )

    suspend fun fetchOverview(): DemoResult {
        val response = client.get("/headers")
        return response.toResult("Request headers")
    }

    fun close() {
        client.close()
        customAdapterClient.close()
    }

    private suspend fun demo(title: String, block: suspend () -> DemoResult): DemoResult =
        try {
            block()
        } catch (error: AxiosError) {
            DemoResult(
                title = title,
                status = error.response?.status,
                statusText = error.response?.statusText,
                durationMs = error.response?.duration,
                body = error.response?.data?.let(::format) ?: "",
                errorCode = error.code,
                errorMessage = buildString {
                    append(error.message ?: "Request failed")
                    if (isCancel(error)) append("\n(canceled)")
                },
            )
        } catch (error: Throwable) {
            DemoResult(
                title = title,
                errorMessage = error.message ?: error::class.simpleName,
            )
        }

    private fun AxiosResponse<Any?>.toResult(title: String): DemoResult =
        DemoResult(
            title = title,
            status = status,
            statusText = statusText,
            durationMs = duration,
            body = format(data),
        )

    private fun format(data: Any?): String = when (data) {
        null -> "(empty)"
        is JsonElement -> pretty.encodeToString(JsonElement.serializer(), data)
        is ByteArray -> "${data.size} bytes"
        is String -> data
        else -> data.toString()
    }
}
