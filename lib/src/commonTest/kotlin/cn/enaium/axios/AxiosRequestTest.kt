@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package cn.enaium.axios

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun mockAxios(
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> io.ktor.client.request.HttpResponseData,
): Axios {
    val engine = MockEngine(handler)
    val client = HttpClient(engine) { expectSuccess = false }
    return Axios(adapter = KtorAdapter(client))
}

private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

@Serializable
private data class TestUser(val id: Int, val name: String)

class URLUtilsTest {

    @Test
    fun encodesQueryComponents() {
        assertEquals("a+b", encodeQueryComponent("a b"))
        assertEquals("a,b", encodeQueryComponent("a,b"))
        assertEquals("a:b", encodeQueryComponent("a:b"))
        assertEquals("%2F", encodeQueryComponent("/"))
    }

    @Test
    fun buildsUrlsWithNestedParams() {
        val url = buildURL(
            "https://example.com/path#hash",
            mapOf(
                "q" to "a b",
                "tags" to listOf("one", "two"),
                "nested" to mapOf("value" to 1),
            ),
        )
        assertEquals(
            "https://example.com/path?q=a+b&tags%5B%5D=one&tags%5B%5D=two&nested%5Bvalue%5D=1",
            url,
        )
    }

    @Test
    fun resolvesBaseAndAbsoluteUrls() {
        assertEquals("https://base.test/a", buildFullPath("https://base.test/", "/a"))
        assertEquals("https://other.test/a", buildFullPath("https://base.test", "https://other.test/a"))
        assertEquals(
            "https://base.test/https://other.test/a",
            buildFullPath("https://base.test", "https://other.test/a", allowAbsoluteUrls = false),
        )
    }
}

class AxiosHeadersTest {

    @Test
    fun normalizesNamesAndPreservesDuplicates() {
        val headers = AxiosHeaders()
        headers["Content-Type"] = "application/json"
        headers.set("X-Multi", listOf("a", "b"))
        headers.set("set-cookie", listOf("a=1", "b=2"))

        assertEquals("application/json", headers["content-type"])
        assertEquals(listOf("a", "b"), headers.getAll("x-multi"))
        assertEquals(listOf("a=1", "b=2"), headers.getSetCookie())
        assertTrue(headers.has("SET-COOKIE"))
    }

    @Test
    fun respectsRewriteFlag() {
        val headers = AxiosHeaders()
        headers.set("X-Test", "first", rewrite = false)
        headers.set("X-Test", "second", rewrite = false)
        assertEquals("first", headers["x-test"])
        headers.set("X-Test", "third")
        assertEquals("third", headers["x-test"])
    }

    @Test
    fun concatenatesWithoutMutatingSources() {
        val first = AxiosHeaders(mapOf("A" to "1", "B" to "1"))
        val second = AxiosHeaders(mapOf("B" to "2", "C" to "3"))
        val merged = AxiosHeaders.concat(first, second)
        assertEquals("1", merged["a"])
        assertEquals("2", merged["b"])
        assertEquals("3", merged["c"])
        assertEquals("1", first["b"])
    }

    @Test
    fun parsesRawHeaderBlock() {
        val headers = AxiosHeaders.from("Content-Type: application/json\nX-Test: 1\nX-Test: 2")
        assertEquals("application/json", headers["content-type"])
        assertEquals("1, 2", headers["x-test"])
    }
}

class AxiosRequestTest {

    @Test
    fun performsGetAndParsesJson() = runTest {
        var captured: HttpRequestData? = null
        val axios = mockAxios { request ->
            captured = request
            respond("""{"id":1,"name":"kotlin"}""", HttpStatusCode.OK, jsonHeaders())
        }

        val response = axios.get("https://example.com/users/1")

        assertEquals(200, response.status)
        val body = assertIs<JsonObject>(response.data)
        assertEquals(1, body["id"]!!.jsonPrimitive.int)
        assertEquals("https://example.com/users/1", captured?.url?.toString())
        assertEquals("get", response.config.method)
    }

    @Test
    fun mergesDefaultAndRequestHeadersAndParams() = runTest {
        var captured: HttpRequestData? = null
        val axios = mockAxios { request ->
            captured = request
            respond("{}", HttpStatusCode.OK, jsonHeaders())
        }
        axios.defaults.baseURL = "https://example.com"
        axios.defaults.headers.common["X-Common"] = "common"
        axios.defaults.headers.get["X-Method"] = "get"

        axios.get(
            "/search",
            AxiosRequestConfig().apply {
                headers = AxiosHeaders(mapOf("X-Request" to "request"))
                params = mapOf("page" to 2)
            },
        )

        assertEquals("common", captured?.headers?.get("X-Common"))
        assertEquals("get", captured?.headers?.get("X-Method"))
        assertEquals("request", captured?.headers?.get("X-Request"))
        assertEquals("https://example.com/search?page=2", captured?.url?.toString())
    }

    @Test
    fun serializesJsonPostBody() = runTest {
        var captured: HttpRequestData? = null
        var sentBody = ""
        val axios = mockAxios { request ->
            captured = request
            sentBody = request.body.toByteArray().decodeToString()
            respond("{}", HttpStatusCode.OK, jsonHeaders())
        }

        axios.post("https://example.com/posts", mapOf("title" to "hello", "count" to 2))

        assertEquals("application/json", captured?.body?.contentType?.toString())
        assertEquals("""{"title":"hello","count":2}""", sentBody)
    }

    @Test
    fun serializesUrlEncodedBodyWhenRequested() = runTest {
        var captured: HttpRequestData? = null
        var sentBody = ""
        val axios = mockAxios { request ->
            captured = request
            sentBody = request.body.toByteArray().decodeToString()
            respond("{}", HttpStatusCode.OK, jsonHeaders())
        }

        axios.post(
            "https://example.com/form",
            mapOf("a" to "b", "space" to "hello world"),
            AxiosRequestConfig().apply {
                headers = AxiosHeaders(mapOf("Content-Type" to "application/x-www-form-urlencoded"))
            },
        )

        assertEquals("a=b&space=hello+world", sentBody)
        assertTrue(captured?.body?.contentType?.toString()!!.contains("x-www-form-urlencoded"))
    }

    @Test
    fun serializesMultipartFormData() = runTest {
        var captured: HttpRequestData? = null
        var sentBody = ""
        val axios = mockAxios { request ->
            captured = request
            sentBody = request.body.toByteArray().decodeToString()
            respond("{}", HttpStatusCode.OK, jsonHeaders())
        }

        val form = FormData().apply {
            append("field", "value")
            append("file", "file-content".encodeToByteArray(), "hello.txt", "text/plain")
        }

        axios.post("https://example.com/upload", form)

        assertTrue(captured?.body?.contentType?.toString()!!.startsWith("multipart/form-data"))
        assertTrue(sentBody.contains("name=\"field\""))
        assertTrue(sentBody.contains("value"))
        assertTrue(sentBody.contains("filename=\"hello.txt\""))
        assertTrue(sentBody.contains("file-content"))
    }

    @Test
    fun supportsTextAndBytesResponseTypes() = runTest {
        val axios = mockAxios { request ->
            if (request.url.encodedPath.endsWith("bytes")) {
                respond(byteArrayOf(1, 2, 3), HttpStatusCode.OK)
            } else {
                respond("plain text", HttpStatusCode.OK)
            }
        }

        val text = axios.get(
            "https://example.com/text",
            AxiosRequestConfig().apply { responseType = ResponseType.Text },
        )
        assertEquals("plain text", text.data)

        val bytes = axios.get(
            "https://example.com/bytes",
            AxiosRequestConfig().apply { responseType = ResponseType.Bytes },
        )
        assertTrue(bytes.data is ByteArray)
        assertEquals(listOf<Byte>(1, 2, 3), (bytes.data as ByteArray).toList())
    }

    @Test
    fun decodesTypedResponses() = runTest {
        val axios = mockAxios { respond("""{"id":7,"name":"Ada"}""", HttpStatusCode.OK, jsonHeaders()) }

        val response = axios.get("https://example.com/user", TestUser.serializer())

        assertEquals(TestUser(7, "Ada"), response.data)
    }

    @Test
    fun decodesReifiedResponses() = runTest {
        val axios = mockAxios { respond("""{"id":9,"name":"Grace"}""", HttpStatusCode.OK, jsonHeaders()) }

        val response = axios.getAs<TestUser>("https://example.com/user")

        assertEquals(TestUser(9, "Grace"), response.data)
    }

    @Test
    fun addsBasicAuthHeader() = runTest {
        var captured: HttpRequestData? = null
        val axios = mockAxios { request ->
            captured = request
            respond("{}", HttpStatusCode.OK, jsonHeaders())
        }

        axios.get(
            "https://example.com/private",
            AxiosRequestConfig().apply { auth = AxiosBasicCredentials("user", "pass") },
        )

        assertEquals("Basic dXNlcjpwYXNz", captured?.headers?.get(HttpHeaders.Authorization))
    }

    @Test
    fun throwsBadRequestForHttpErrorsAndTransformsBody() = runTest {
        val axios = mockAxios {
            respond("""{"message":"missing"}""", HttpStatusCode.NotFound, jsonHeaders())
        }

        val error = assertFailsWith<AxiosError> {
            axios.get("https://example.com/missing")
        }

        assertEquals(AxiosError.ERR_BAD_REQUEST, error.code)
        assertEquals(404, error.status)
        assertNotNull(error.response)
        val body = assertIs<JsonObject>(error.response!!.data)
        assertEquals("missing", body["message"]?.jsonPrimitive?.content)
    }

    @Test
    fun customValidateStatusAcceptsErrorCodes() = runTest {
        val axios = mockAxios { respond("{\"ok\":false}", HttpStatusCode.BadRequest, jsonHeaders()) }

        val response = axios.get(
            "https://example.com/bad",
            AxiosRequestConfig().apply { validateStatus = ValidateStatus { true } },
        )

        assertEquals(400, response.status)
    }

    @Test
    fun enforcesMaxContentLength() = runTest {
        val axios = mockAxios {
            respond(
                "0123456789",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentLength, "10"),
            )
        }

        val error = assertFailsWith<AxiosError> {
            axios.get(
                "https://example.com/large",
                AxiosRequestConfig().apply { maxContentLength = 5 },
            )
        }

        assertEquals(AxiosError.ERR_BAD_RESPONSE, error.code)
    }

    @Test
    fun reportsUploadAndDownloadProgress() = runTest {
        val uploadEvents = mutableListOf<AxiosProgressEvent>()
        val downloadEvents = mutableListOf<AxiosProgressEvent>()
        val axios = mockAxios { request ->
            request.body.toByteArray()
            respond(
                "0123456789",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentLength, "10"),
            )
        }

        axios.post(
            "https://example.com/progress",
            "hello",
            AxiosRequestConfig().apply {
                onUploadProgress = { uploadEvents += it }
                onDownloadProgress = { downloadEvents += it }
            },
        )

        assertEquals(5, uploadEvents.last().loaded)
        assertEquals(5, uploadEvents.last().total)
        assertTrue(uploadEvents.last().upload)
        assertEquals(10, downloadEvents.last().loaded)
        assertEquals(10, downloadEvents.last().total)
        assertTrue(downloadEvents.last().download)
    }

    @Test
    fun mapsTimeoutToAbortedError() = runTest {
        val axios = mockAxios {
            delay(10_000)
            respond("{}", HttpStatusCode.OK, jsonHeaders())
        }

        val error = assertFailsWith<AxiosError> {
            axios.get(
                "https://example.com/slow",
                AxiosRequestConfig().apply { timeout = 100 },
            )
        }

        assertEquals(AxiosError.ECONNABORTED, error.code)
        assertTrue(error.message!!.contains("timeout"))
    }

    @Test
    fun mapsTimeoutWithClarifyOption() = runTest {
        val axios = mockAxios {
            delay(10_000)
            respond("{}", HttpStatusCode.OK, jsonHeaders())
        }

        val error = assertFailsWith<AxiosError> {
            axios.get(
                "https://example.com/slow",
                AxiosRequestConfig().apply {
                    timeout = 100
                    transitional = TransitionalOptions(clarifyTimeoutError = true)
                },
            )
        }

        assertEquals(AxiosError.ETIMEDOUT, error.code)
    }

    @Test
    fun cancelsThroughCancelToken() = runTest {
        val gate = CompletableDeferred<Unit>()
        var started = false
        val axios = mockAxios {
            started = true
            gate.await()
            respond("{}", HttpStatusCode.OK, jsonHeaders())
        }
        val source = CancelToken.source()

        val deferred = async {
            runCatching {
                axios.get(
                    "https://example.com/slow",
                    AxiosRequestConfig().apply { cancelToken = source.token },
                )
            }
        }
        runCurrent()

        source.cancel("stop")
        gate.complete(Unit)
        val error = deferred.await().exceptionOrNull()

        assertIs<CanceledError>(error)
        assertEquals(AxiosError.ERR_CANCELED, (error as AxiosError).code)
        assertTrue(isCancel(error))
    }

    @Test
    fun rejectsAlreadyCancelledToken() = runTest {
        val source = CancelToken.source()
        source.cancel("before")
        val axios = mockAxios { respond("{}", HttpStatusCode.OK, jsonHeaders()) }

        val error = assertFailsWith<CanceledError> {
            axios.get(
                "https://example.com/never",
                AxiosRequestConfig().apply { cancelToken = source.token },
            )
        }
        assertEquals("before", error.message)
    }

    @Test
    fun usesCustomAdapter() = runTest {
        val adapter = object : AxiosAdapter {
            override suspend fun request(config: AxiosRequestConfig): AxiosResponse<Any?> =
                AxiosResponse(
                    data = """{"adapter":"custom"}""",
                    status = 200,
                    statusText = "OK",
                    headers = AxiosHeaders(mapOf("X-Adapter" to "custom")),
                    config = config,
                )
        }
        val axios = Axios(adapter = adapter)

        val response = axios.get("https://example.com/custom")

        val body = assertIs<JsonObject>(response.data)
        assertEquals("custom", body["adapter"]?.jsonPrimitive?.content)
        assertEquals("custom", response.headers["x-adapter"])
    }

    @Test
    fun buildsUrisWithoutSending() {
        val axios = Axios(adapter = NoopAdapter)
        axios.defaults.baseURL = "https://example.com"
        axios.defaults.params = mapOf("lang" to "kotlin")

        val uri = axios.getUri(
            AxiosRequestConfig().apply {
                url = "/docs"
                params = mapOf("page" to 1)
            },
        )

        assertEquals("https://example.com/docs?lang=kotlin&page=1", uri)
    }

    @Test
    fun createsInstancesWithInheritedDefaults() {
        val parent = Axios(adapter = NoopAdapter)
        parent.defaults.baseURL = "https://parent.test"
        parent.defaults.headers.common["X-Parent"] = "yes"

        val child = parent.create { baseURL = "https://child.test" }

        assertEquals("https://child.test", child.defaults.baseURL)
        assertEquals("yes", child.defaults.headers.common["X-Parent"])
        assertEquals("https://parent.test", parent.defaults.baseURL)
    }

    @Test
    fun reportsNetworkFailures() = runTest {
        val axios = mockAxios { throw IllegalStateException("boom") }

        val error = assertFailsWith<AxiosError> {
            axios.get("https://example.com/network")
        }

        assertEquals(AxiosError.ERR_NETWORK, error.code)
        assertEquals("boom", error.message)
    }

    @Test
    fun rejectsInvalidUrls() = runTest {
        val axios = mockAxios { respond("{}", HttpStatusCode.OK, jsonHeaders()) }

        val error = assertFailsWith<AxiosError> {
            axios.get("/relative-only")
        }

        assertEquals(AxiosError.ERR_INVALID_URL, error.code)
    }
}

private object NoopAdapter : AxiosAdapter {
    override suspend fun request(config: AxiosRequestConfig): AxiosResponse<Any?> =
        AxiosResponse("", 200, "OK", AxiosHeaders(), config)
}

class FormAndErrorTest {

    @Test
    fun postFormSendsMultipartBodyFromMap() = runTest {
        var captured: HttpRequestData? = null
        var body = ""
        val axios = mockAxios { request ->
            captured = request
            body = request.body.toByteArray().decodeToString()
            respond("{}", HttpStatusCode.OK, jsonHeaders())
        }

        axios.postForm("https://example.com/form", mapOf("name" to "Axios KMP", "count" to 2))

        assertTrue(captured!!.body.contentType.toString().startsWith("multipart/form-data"))
        assertTrue(body.contains("name=\"name\""))
        assertTrue(body.contains("Axios KMP"))
        assertTrue(body.contains("name=\"count\""))
    }

    @Test
    fun redactsConfiguredKeysInErrorJson() {
        val config = AxiosRequestConfig().apply {
            redact = listOf("authorization")
            headers = AxiosHeaders(
                mapOf(
                    "Authorization" to "Bearer secret",
                    "X-Trace" to "visible",
                )
            )
        }
        val error = AxiosError("failure", AxiosError.ERR_BAD_REQUEST, config)

        @Suppress("UNCHECKED_CAST")
        val serialized = error.toJSON()["config"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val headers = serialized["headers"] as Map<String, Any?>

        assertEquals("[REDACTED ****]", headers["Authorization"])
        assertEquals("visible", headers["X-Trace"])
    }

    @Test
    fun mergesTwoConfigsWithoutMutatingInputs() {
        val first = AxiosRequestConfig().apply {
            baseURL = "https://example.com"
            headers = AxiosHeaders(mapOf("A" to "1"))
            params = mapOf("a" to 1)
        }
        val second = AxiosRequestConfig().apply {
            url = "/path"
            headers = AxiosHeaders(mapOf("B" to "2"))
            params = mapOf("b" to 2)
        }

        val merged = mergeConfig(first, second)

        assertEquals("https://example.com", merged.baseURL)
        assertEquals("/path", merged.url)
        assertEquals("1", merged.headers!!["A"])
        assertEquals("2", merged.headers!!["B"])
        assertEquals(mapOf("a" to 1, "b" to 2), merged.params)
        assertEquals(null, first.url)
        assertEquals(null, first.headers!!["B"])
    }
}

class HeaderNormalizationTest {

    @Test
    fun formattedHeadersRemainCaseInsensitive() {
        val headers = AxiosHeaders(mapOf("content-type" to "application/json"))
        headers.normalize(format = true)

        assertEquals("application/json", headers["Content-Type"])
        assertEquals("application/json", headers["content-type"])
        assertTrue(headers.has("CONTENT-TYPE"))

        headers["X-Test"] = "1"
        assertEquals("1", headers["x-test"])
        assertTrue(headers.entries.any { it.first == "Content-Type" })
        assertTrue(headers.toJSON().containsKey("Content-Type"))
    }
}

class TypedDecodeTest {

    @Test
    fun wrapsDeserializationFailures() = runTest {
        val axios = mockAxios {
            respond("""{"id":"not-a-number"}""", HttpStatusCode.OK, jsonHeaders())
        }

        val error = assertFailsWith<AxiosError> {
            axios.get("https://example.com/user", TestUser.serializer())
        }

        assertEquals(AxiosError.ERR_BAD_RESPONSE, error.code)
    }
}
