package cn.enaium.axios

/**
 * Minimal multiplatform replacement for the browser/Node `URLSearchParams`
 * used by axios.
 */
class URLSearchParams(initial: Any? = null) {

    private val pairs = mutableListOf<Pair<String, String>>()

    init {
        if (initial != null) {
            serializeFormEntries(initial, FormSerializerOptions()).forEach { (name, value) ->
                pairs += name to value
            }
        }
    }

    val size: Int get() = pairs.size

    fun append(name: String, value: Any?) {
        pairs += name to stringify(value)
    }

    fun delete(name: String) {
        pairs.removeAll { it.first == name }
    }

    fun get(name: String): String? = pairs.firstOrNull { it.first == name }?.second

    fun getAll(name: String): List<String> =
        pairs.filter { it.first == name }.map { it.second }

    fun has(name: String): Boolean = pairs.any { it.first == name }

    fun set(name: String, value: Any?) {
        delete(name)
        append(name, value)
    }

    fun sort() {
        pairs.sortBy { it.first }
    }

    fun entries(): List<Pair<String, String>> = pairs.toList()

    override fun toString(): String =
        pairs.joinToString("&") { (name, value) ->
            "${encodeFormComponent(name)}=${encodeFormComponent(value)}"
        }

    private fun stringify(value: Any?): String = when (value) {
        null -> ""
        is ByteArray -> base64Encode(value)
        else -> value.toString()
    }
}

/**
 * Multipart form payload.
 *
 * Values passed as [ByteArray] are emitted as file parts; other values are
 * emitted as UTF-8 text fields.
 */
class FormData {

    class Part internal constructor(
        val name: String,
        val value: ByteArray,
        val filename: String? = null,
        val contentType: String? = null,
    ) {
        val isFile: Boolean get() = filename != null || contentType != null

        fun text(): String = value.decodeToString()
    }

    private val parts = mutableListOf<Part>()

    val size: Int get() = parts.size

    fun append(name: String, value: String) {
        parts += Part(name, value.encodeToByteArray())
    }

    fun append(name: String, value: Any?) {
        when (value) {
            is ByteArray -> append(name, value, null, null)
            null -> append(name, "")
            else -> append(name, value.toString())
        }
    }

    fun append(
        name: String,
        value: ByteArray,
        filename: String? = null,
        contentType: String? = null,
    ) {
        parts += Part(name, value, filename, contentType)
    }

    fun set(name: String, value: Any?) {
        delete(name)
        append(name, value)
    }

    fun set(
        name: String,
        value: ByteArray,
        filename: String? = null,
        contentType: String? = null,
    ) {
        delete(name)
        append(name, value, filename, contentType)
    }

    fun delete(name: String) {
        parts.removeAll { it.name == name }
    }

    fun has(name: String): Boolean = parts.any { it.name == name }

    fun get(name: String): Part? = parts.firstOrNull { it.name == name }

    fun getAll(name: String): List<Part> = parts.filter { it.name == name }

    fun entries(): List<Part> = parts.toList()

    /**
     * Converts the form parts back to a nested JSON-compatible map. File parts
     * are decoded as UTF-8 text, matching axios' `formToJSON`.
     */
    fun toJSON(): Map<String, Any?> = formPartsToJSON(
        parts.map { it.name to it.value.decodeToString() }
    )
}

/**
 * Converts an object graph (maps, iterables, JSON values) into [FormData].
 */
fun toFormData(
    source: Any?,
    target: FormData = FormData(),
    options: FormSerializerOptions = FormSerializerOptions(),
): FormData {
    serializeRawFormEntries(source, options, multipart = true).forEach { (name, value) ->
        when (value) {
            is ByteArray -> target.append(name, value)
            null -> target.append(name, "")
            else -> target.append(name, value.toString())
        }
    }
    return target
}

/**
 * Converts [FormData] into a nested JSON-compatible map.
 */
fun formDataToJSON(form: FormData): Any? = form.toJSON()

/**
 * Converts a map-like object graph to `application/x-www-form-urlencoded`
 * parameter pairs.
 */
fun toURLEncodedForm(
    source: Any?,
    options: FormSerializerOptions = FormSerializerOptions(),
): URLSearchParams {
    val result = URLSearchParams()
    serializeFormEntries(source, options).forEach { (name, value) -> result.append(name, value) }
    return result
}

internal fun serializeFormEntries(
    source: Any?,
    options: FormSerializerOptions,
): List<Pair<String, String>> =
    serializeRawFormEntries(source, options, multipart = false).map { (name, value) ->
        name to when (value) {
            null -> ""
            is ByteArray -> base64Encode(value)
            else -> value.toString()
        }
    }

private fun serializeRawFormEntries(
    source: Any?,
    options: FormSerializerOptions,
    multipart: Boolean,
): List<Pair<String, Any?>> {
    val result = mutableListOf<Pair<String, Any?>>()

    fun render(path: List<String>): String {
        if (path.isEmpty()) return ""
        val sb = StringBuilder()
        path.forEachIndexed { index, token ->
            when {
                index == 0 -> sb.append(token)
                token.startsWith("[") && token.endsWith("]") -> sb.append(token)
                options.dots -> sb.append('.').append(token)
                else -> sb.append('[').append(token).append(']')
            }
        }
        return sb.toString()
    }

    fun visit(value: Any?, path: List<String>, depth: Int) {
        if (depth > options.maxDepth) {
            throw AxiosError(
                "FormData field is too deeply nested ($depth levels). Max depth: ${options.maxDepth}",
                AxiosError.ERR_FORM_DATA_DEPTH_EXCEEDED,
            )
        }
        when (value) {
            null -> result += render(path) to ""
            is Map<*, *> -> value.forEach { (key, item) ->
                if (key != null) visit(item, path + key.toString(), depth + 1)
            }
            is Iterable<*> -> {
                value.forEachIndexed { index, item ->
                    val next = when {
                        options.indexes -> path + "[$index]"
                        options.metaTokens -> path + "[]"
                        else -> path
                    }
                    visit(item, next, depth + 1)
                }
            }
            is Array<*> -> value.forEachIndexed { index, item ->
                val next = when {
                    options.indexes -> path + "[$index]"
                    options.metaTokens -> path + "[]"
                    else -> path
                }
                visit(item, next, depth + 1)
            }
            is ByteArray -> result += render(path) to value
            else -> result += render(path) to value
        }
    }

    when (source) {
        is URLSearchParams -> source.entries().forEach { (name, value) -> result += name to value }
        is FormData -> {
            if (!multipart) {
                source.entries().forEach { part -> result += part.name to part.text() }
            } else {
                source.entries().forEach { part ->
                    result += part.name to if (part.filename != null || part.contentType != null) part.value else part.text()
                }
            }
        }
        else -> visit(source, emptyList(), 0)
    }
    return result
}

private fun formPartsToJSON(pairs: List<Pair<String, String>>): Map<String, Any?> {
    val root = LinkedHashMap<String, Any?>()
    pairs.forEach { (name, value) ->
        val path = parsePropPath(name)
        if (path.isEmpty()) return@forEach
        var current: MutableMap<String, Any?> = root
        path.forEachIndexed { index, token ->
            if (index == path.lastIndex) {
                current[token] = value
            } else {
                @Suppress("UNCHECKED_CAST")
                current = current.getOrPut(token) { LinkedHashMap<String, Any?>() } as MutableMap<String, Any?>
            }
        }
    }
    return root
}

private fun parsePropPath(name: String): List<String> {
    val path = mutableListOf<String>()
    val pattern = Regex("[^.[\\]]+|\\[([^.[\\]]*)]")
    pattern.findAll(name).forEach { match ->
        val text = match.value
        path += if (text == "[]") "" else match.groupValues[1].ifEmpty { text }
    }
    return path
}
