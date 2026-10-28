# Axios KMP

A Kotlin Multiplatform port of [axios](https://github.com/axios/axios) 1.21.0. The default transport is [Ktor HttpClient](https://ktor.io/docs/client.html); every axios concept — defaults, interceptors, cancellation, timeouts, progress, transforms, form data, params serialization, error codes and `validateStatus` — is preserved, expressed with coroutines and Kotlin types.

Supported targets: **Android, JVM/Desktop, iOS, macOS, tvOS, watchOS, Linux, Windows (MinGW), Kotlin/JS and Kotlin/Wasm**.

**Live sample:** [https://enaium.github.io/axios-kmp/](https://enaium.github.io/axios-kmp/)

## Installation

```kotlin
implementation("cn.enaium.axios:axios-kmp:1.0.0")
```

The default Ktor engine is selected per platform and is published as a runtime dependency:

| Platform | Ktor engine |
| --- | --- |
| Android, JVM | OkHttp |
| iOS, macOS, tvOS, watchOS | Darwin |
| Linux | CIO |
| Windows (MinGW) | WinHTTP |
| Kotlin/JS, Kotlin/Wasm | Js |

No engine setup is needed. To use a custom engine, build a `HttpClient` and pass it to `KtorAdapter`.

## Quick start

```kotlin
import cn.enaium.axios.axios
import kotlinx.serialization.json.JsonElement

suspend fun main() {
    val response = axios.get("https://httpbin.org/get")

    println(response.status)                 // 200
    println(response.statusText)             // OK
    println(response.duration)               // adapter time in milliseconds
    println(response.headers["content-type"])
    println(response.data as JsonElement)    // JSON bodies are parsed by default
}
```

### Instances and defaults

```kotlin
import cn.enaium.axios.create

val api = create {
    baseURL = "https://api.example.com"
    timeout = 15_000
    headers.common["X-Client"] = "my-app"
    headers.post["Content-Type"] = "application/json"
}

// Absolute URLs bypass baseURL (axios default):
api.get("https://other.example.com/health")
```

`create` returns a new `Axios` inheriting the shared defaults. Use `Axios()` for a fully independent instance.

### JSON and typed responses

Request bodies accept `JsonElement`, maps, lists, primitives, `String`, `ByteArray`, `FormData` and `URLSearchParams`. Maps and lists are JSON-encoded automatically.

```kotlin
api.post("/users", mapOf("name" to "Ada", "admin" to true))

// @Serializable models: use the reified helper or pass a serializer explicitly.
@Serializable
data class User(val id: Int, val name: String)

val user: User = api.getAs<User>("/users/1").data
val same = api.get("/users/1", User.serializer()).data

// Encode a @Serializable object as a request body:
api.post("/users", jsonBody(User(2, "Grace")))
```

For a non-default `Json` configuration, set `json` in the instance defaults or in the request config.

### Request config

```kotlin
val response = api.request(
    AxiosRequestConfig().apply {
        url = "/search"
        method = "get"
        params = mapOf("q" to "kotlin", "page" to 2)
        headers = AxiosHeaders(mapOf("Accept-Language" to "en"))
        timeout = 5_000
        responseType = ResponseType.Json
        validateStatus = ValidateStatus { it in 200..299 }
        onDownloadProgress = { event -> println("${event.loaded}/${event.total}") }
    }
)
```

`AxiosRequestConfig` supports: `url`, `method`, `baseURL`, `allowAbsoluteUrls`, `params`, `paramsSerializer`, `headers`, `data`, `timeout`, `timeoutErrorMessage`, `auth`, `responseType`, `responseEncoding`, `transformRequest`, `transformResponse`, `validateStatus`, `onUploadProgress`, `onDownloadProgress`, `maxContentLength`, `maxBodyLength`, `maxRedirects`, `cancelToken`, `withCredentials`, `withXSRFToken`, `xsrfCookieName`, `xsrfHeaderName`, `decompress`, `transitional`, `adapter`, `formSerializer`, `proxy`, `redact`, `sensitiveHeaders` and `json`.

Convenience overloads accept a trailing config block:

```kotlin
api.get("/users") {
    params = mapOf("page" to 1)
    timeout = 3_000
}
```

Method aliases: `get`, `delete`, `head`, `options`, `post`, `put`, `patch`, `query`, plus `postForm`, `putForm`, `patchForm`.

### Headers

`AxiosHeaders` is case-insensitive, preserves repeated values (for example `Set-Cookie`) and can be built from maps or raw header blocks.

```kotlin
val headers = AxiosHeaders()
headers["Content-Type"] = "application/json"
headers.set("X-Trace", listOf("one", "two"))
headers.getAll("x-trace")        // [one, two]
headers.getContentType()         // application/json
headers.delete("X-Trace")
```

### Interceptors

```kotlin
val id = api.interceptors.request.use { config ->
    config.copy().apply {
        headers = AxiosHeaders.from(headers).apply { set("Authorization", "Bearer $token") }
    }
}

api.interceptors.response.use { response ->
    log(response.status, response.duration)
    response
}

api.interceptors.request.eject(id)   // remove one
api.interceptors.request.clear()     // remove all
```

Request interceptors run in reverse registration order by default, matching axios. Set `transitional = TransitionalOptions(legacyInterceptorReqResOrdering = false)` on a request to run them in registration order. `AxiosInterceptorOptions.runWhen` skips an interceptor for matching configs.

Rejection handlers can recover:

```kotlin
api.interceptors.response.use(onRejected = { error ->
    if (error is AxiosError && error.response?.status == 401) {
        refreshToken()
        api.request(error.config!!)
    } else {
        throw error
    }
})
```

Callbacks are declared after options in the Kotlin signatures so trailing lambdas bind to the interceptor callback. `synchronous` is accepted for API compatibility but ignored; interceptors are always awaited.

### Cancellation and timeouts

```kotlin
val source = CancelToken.source()

val job = launch {
    try {
        api.get("/slow", AxiosRequestConfig().apply { cancelToken = source.token })
    } catch (error: CanceledError) {
        // ERR_CANCELED
    }
}

source.cancel("user navigated away")
```

Cancelling the calling coroutine also cancels the underlying Ktor request and propagates `CancellationException` normally, so structured concurrency keeps working.

`timeout` uses coroutine cancellation:

- default: `AxiosError.ECONNABORTED`
- `clarifyTimeoutError = true`: `AxiosError.ETIMEDOUT`
- message: `timeoutErrorMessage` or `timeout of {n}ms exceeded`

### Errors

```kotlin
try {
    api.get("/missing")
} catch (error: AxiosError) {
    error.code                    // ERR_BAD_REQUEST for 4xx, ERR_BAD_RESPONSE otherwise
    error.status                  // 404
    error.response?.data          // transformed error body
    error.config
    error.request
    error.cause
    error.toJSON()                // honors config.redact
}
```

Other codes: `ERR_NETWORK`, `ERR_INVALID_URL`, `ERR_CANCELED`, `ETIMEDOUT`, `ECONNABORTED`, `ERR_FORM_DATA_DEPTH_EXCEEDED`, `ERR_BAD_OPTION_VALUE`.

`isAxiosError(value)` and `isCancel(value)` helpers are available.

### Progress

```kotlin
api.post(
    "/upload",
    FormData().apply {
        append("title", "Axios KMP")
        append("file", bytes, "report.pdf", "application/pdf")
    },
    AxiosRequestConfig().apply {
        onUploadProgress = { event -> println("upload ${event.loaded}/${event.total}") }
        onDownloadProgress = { event -> println("download ${event.loaded}/${event.total}") }
    },
)
```

`AxiosProgressEvent` includes `loaded`, `total`, `progress`, `bytes`, `rate`, `estimated`, `upload`, `download` and `lengthComputable`.

### Params serialization

```kotlin
api.get("/items", AxiosRequestConfig().apply {
    params = mapOf(
        "tags" to listOf("a", "b"),          // tags%5B%5D=a&tags%5B%5D=b
        "filter" to mapOf("min" to 1),       // filter%5Bmin%5D=1
    )
    formSerializer = FormSerializerOptions(indexes = true, dots = false)
})
```

Custom serializers implement `ParamsSerializer`:

```kotlin
api.get("/items", AxiosRequestConfig().apply {
    params = rawQuery
    paramsSerializer = ParamsSerializer { params, _ -> params.toString() }
})
```

### Custom adapters

```kotlin
class MyAdapter : AxiosAdapter {
    override suspend fun request(config: AxiosRequestConfig): AxiosResponse<Any?> =
        AxiosResponse(
            data = """{"source":"custom"}""",
            status = 200,
            statusText = "OK",
            headers = AxiosHeaders(),
            config = config,
        )
}

val api = Axios(adapter = MyAdapter())
```

To keep the Ktor adapter but customize the engine/plugins:

```kotlin
val client = HttpClient(OkHttp) {
    install(Logging)
    engine { config { retryOnConnectionFailure(true) } }
}
val api = Axios(adapter = KtorAdapter(client))
```

`KtorAdapter(client)` does not close a client you own. `KtorAdapter()` closes its internal client; `Axios.close()` delegates to the adapter. Instances produced by `create` share the parent transport, so closing one closes the shared `HttpClient`; build `Axios(adapter = KtorAdapter())` for an independent transport.

## Axios parity

Kotlin has different primitives than JavaScript, so the port makes the following deliberate choices:

| axios | Axios KMP |
| --- | --- |
| `Promise<AxiosResponse>` | `suspend fun request(...): AxiosResponse` |
| `any` response data parsed to JS objects | `JsonElement` by default, `String` for text, `ByteArray` for byte response types |
| `axios.get<T>()` TypeScript generics | `getAs<T>()` reified helper or `get(url, serializer)` |
| `CancelToken` | `CancelToken` / `CancelTokenSource`, plus coroutine cancellation |
| `onUploadProgress` / `onDownloadProgress` | Same callback names with `AxiosProgressEvent` |
| `AxiosHeaders` | Same concepts: case-insensitive, multi-value, `set`/`get`/`has`/`delete`/`concat`/`normalize` |
| `toFormData` / `formToJSON` | `toFormData` / `formDataToJSON` |
| `axios.all` / `axios.spread` | Use `coroutineScope`, `async` and `awaitAll` |

Engine-level options that Ktor owns are accepted on the config for source compatibility but applied through the `HttpClient`/engine instead: `proxy`, `decompress`, `withCredentials`, `xsrf*`, `maxRedirects`. Ktor follows redirects according to the client configuration; use a custom client or install `HttpRedirect` options to change that. `responseEncoding` currently decodes UTF-8 only. `ResponseType.Stream` is buffered into a `ByteArray` rather than streamed lazily. `synchronous` is ignored because interceptors are suspending.

`redact` is honored by `AxiosError.toJSON()`; `sensitiveHeaders` is accepted but not applied.

## Sample

The `sample` module is a Compose Multiplatform playground that runs real requests against `httpbin.org` and `jsonplaceholder.typicode.com`, covering GET/POST, typed responses, multipart, 404s, timeouts, cancellation, byte responses and a custom adapter.

```bash
./gradlew :sample:desktopApp:run
./gradlew :sample:androidApp:assembleDebug
./gradlew :sample:webApp:wasmJsBrowserDevelopmentRun
./gradlew :sample:webApp:jsBrowserDevelopmentRun
```

Android installs through the generated `sample/androidApp/build/outputs/apk/debug/androidApp-debug.apk`. iOS exposes `MainViewController()` from the `Sample` framework.

### Deploying the web sample

`.github/workflows/deploy-sample.yml` builds `:sample:webApp:composeCompatibilityBrowserDistribution` on every push to `main` and publishes the result to the `gh-pages` branch with `peaceiris/actions-gh-pages`. Select **Deploy from a branch → gh-pages** in the repository Pages settings. The workflow can also be started manually with `workflow_dispatch`.

## Project layout

```
lib/                              KMP library (commonMain contains the full implementation)
sample/                           shared Compose Multiplatform examples
sample/androidApp/                Android application
sample/desktopApp/                desktop application
sample/webApp/                    JS + Wasm browser application
```

## Build and test

```bash
./gradlew :lib:jvmTest
./gradlew :lib:compileKotlinJs :lib:compileKotlinWasmJs
./gradlew :lib:compileKotlinIosSimulatorArm64
./gradlew :sample:androidApp:assembleDebug
```

### Publishing locally

```bash
./gradlew :lib:publishToMavenLocal
```

Then add `mavenLocal()` to the consumer repositories and depend on:

```kotlin
implementation("cn.enaium.axios:axios-kmp:1.0.0")
```

The test suite uses Ktor `MockEngine` and covers defaults merging, headers, params, URL building, JSON/urlencoded/multipart bodies, typed decoding, status validation, error mapping, timeout codes, cancellation, interceptors, progress and custom adapters.

## License

MIT. Portions derived from axios, Copyright (c) 2014-present Matt Zabriskie & Collaborators.
