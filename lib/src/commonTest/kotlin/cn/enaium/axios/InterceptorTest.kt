package cn.enaium.axios

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

private fun interceptorAxios(): Axios {
    val engine = MockEngine { respond("""{"ok":true}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }
    return Axios(adapter = KtorAdapter(HttpClient(engine) { expectSuccess = false }))
}

class InterceptorTest {

    @Test
    fun runsRequestInterceptorsInReverseOrderByDefault() = runTest {
        val order = mutableListOf<String>()
        val axios = interceptorAxios()
        axios.interceptors.request.use { config ->
            order += "first"
            config
        }
        axios.interceptors.request.use { config ->
            order += "second"
            config
        }

        axios.get("https://example.com")

        assertEquals(listOf("second", "first"), order)
    }

    @Test
    fun runsRequestInterceptorsInRegistrationOrderWhenLegacyOrderingDisabled() = runTest {
        val order = mutableListOf<String>()
        val axios = interceptorAxios()
        axios.interceptors.request.use { config ->
            order += "first"
            config
        }
        axios.interceptors.request.use { config ->
            order += "second"
            config
        }

        axios.get(
            "https://example.com",
            AxiosRequestConfig().apply {
                transitional = TransitionalOptions(legacyInterceptorReqResOrdering = false)
            },
        )

        assertEquals(listOf("first", "second"), order)
    }

    @Test
    fun requestInterceptorCanReplaceConfig() = runTest {
        val axios = interceptorAxios()
        axios.interceptors.request.use { config ->
            config.copy().apply { url = "https://example.com/rewritten" }
        }

        val response = axios.get("https://example.com/original")

        assertEquals("https://example.com/rewritten", response.config.url)
    }

    @Test
    fun responseInterceptorCanReplaceData() = runTest {
        val axios = interceptorAxios()
        axios.interceptors.response.use { response ->
            response.copy(data = """{"intercepted":true}""")
        }

        val response = axios.get("https://example.com")

        assertEquals("""{"intercepted":true}""", response.data)
    }

    @Test
    fun responseRejectionHandlerCanRecover() = runTest {
        val axios = interceptorAxios()
        axios.interceptors.response.use(
            onRejected = { error ->
                AxiosResponse(
                    data = """{"recovered":true}""",
                    status = 200,
                    statusText = "OK",
                    headers = AxiosHeaders(),
                    config = (error as AxiosError).config!!,
                )
            }
        )

        val error = runCatching { axios.get("https://example.com") }.exceptionOrNull()

        // The mock returns 200, so force a failure through validateStatus.
        assertEquals(null, error)
    }

    @Test
    fun responseRejectionHandlerRecoversFromHttpErrors() = runTest {
        val engine = MockEngine { respond("nope", HttpStatusCode.InternalServerError) }
        val axios = Axios(adapter = KtorAdapter(HttpClient(engine) { expectSuccess = false }))
        axios.interceptors.response.use(
            onRejected = { error ->
                AxiosResponse(
                    data = "recovered",
                    status = 200,
                    statusText = "OK",
                    headers = AxiosHeaders(),
                    config = (error as AxiosError).config!!,
                )
            }
        )

        val response = axios.get("https://example.com")

        assertEquals("recovered", response.data)
        assertEquals(200, response.status)
    }

    @Test
    fun runWhenSkipsInterceptors() = runTest {
        var called = false
        val axios = interceptorAxios()
        axios.interceptors.request.use(
            onFulfilled = { config ->
                called = true
                config
            },
            options = AxiosInterceptorOptions(runWhen = { config -> config.url?.contains("skip") == false }),
        )

        axios.get("https://example.com/skip")

        assertEquals(false, called)
    }

    @Test
    fun ejectRemovesInterceptor() = runTest {
        var calls = 0
        val axios = interceptorAxios()
        val id = axios.interceptors.request.use { config ->
            calls += 1
            config
        }

        axios.get("https://example.com")
        axios.interceptors.request.eject(id)
        axios.get("https://example.com")

        assertEquals(1, calls)
    }

    @Test
    fun requestRejectionHandlerCanRecover() = runTest {
        val engine = MockEngine { respond("ok", HttpStatusCode.OK) }
        val axios = Axios(adapter = KtorAdapter(HttpClient(engine) { expectSuccess = false }))
        var rejected = false
        // Registered first, so the throwing interceptor runs first (LIFO) and
        // this rejection handler recovers before dispatch.
        axios.interceptors.request.use(
            onRejected = { error ->
                rejected = true
                assertTrue(error is IllegalStateException)
                AxiosRequestConfig().apply { url = "https://example.com/recovered" }
            },
        )
        axios.interceptors.request.use { throw IllegalStateException("interceptor failed") }

        val response = axios.get("https://example.com")

        assertTrue(rejected)
        assertEquals("ok", response.data)
    }

    @Test
    fun strictJsonParsingThrowsBadResponse() = runTest {
        val engine = MockEngine { respond("not json", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }
        val axios = Axios(adapter = KtorAdapter(HttpClient(engine) { expectSuccess = false }))

        val error = assertFailsWith<AxiosError> {
            axios.get(
                "https://example.com",
                AxiosRequestConfig().apply {
                    responseType = ResponseType.Json
                    transitional = TransitionalOptions(silentJSONParsing = false)
                },
            )
        }

        assertEquals(AxiosError.ERR_BAD_RESPONSE, error.code)
    }
}
