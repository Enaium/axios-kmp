package cn.enaium.axios

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement

/**
 * Default JSON configuration used for request serialization and response
 * parsing. It is lenient, ignores unknown keys when decoding and omits nulls
 * explicitly configured by callers.
 */
val DefaultJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

/**
 * Serializes [value] into a [JsonElement] suitable for `AxiosRequestConfig.data`.
 *
 * Requires the kotlinx.serialization compiler plugin for custom `@Serializable`
 * types, exactly like any other reified serialization API.
 */
inline fun <reified T> jsonBody(value: T, json: Json = DefaultJson): JsonElement =
    json.encodeToJsonElement(value)

/**
 * Converts common Kotlin values into a [JsonElement].
 *
 * Supported: `null`, [String], [Number], [Boolean], [JsonElement], maps with
 * string keys, iterables and arrays. Other types raise [AxiosError] with
 * [AxiosError.ERR_BAD_OPTION_VALUE]; use [jsonBody] for `@Serializable` types.
 */
fun Any?.asJsonElement(json: Json = DefaultJson): JsonElement = when (this) {
    null -> JsonNull
    is JsonElement -> this
    is String -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is Map<*, *> -> JsonObject(
        entries
            .filter { it.key != null }
            .associate { it.key.toString() to it.value.asJsonElement(json) }
    )
    is Iterable<*> -> JsonArray(map { it.asJsonElement(json) })
    is Array<*> -> JsonArray(map { it.asJsonElement(json) })
    else -> throw AxiosError(
        "Cannot serialize request data of type ${this::class.simpleName}. " +
            "Use a JsonElement, Map, List, primitive, String or ByteArray.",
        AxiosError.ERR_BAD_OPTION_VALUE,
    )
}

internal fun JsonElement.stringValueOrNull(): String? =
    (this as? JsonPrimitive)?.takeIf { it.isString }?.content

internal fun String.toJsonPrimitive(): JsonPrimitive = JsonPrimitive(this)
