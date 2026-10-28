package cn.enaium.axios

private val absoluteUrlRegex = Regex("^([a-z][a-z\\d+\\-.]*:)?//", RegexOption.IGNORE_CASE)

/**
 * Returns `true` when [url] is absolute, including protocol-relative URLs.
 */
fun isAbsoluteURL(url: String?): Boolean {
    if (url == null) return false
    return absoluteUrlRegex.containsMatchIn(url)
}

/**
 * Combines a base URL with a relative URL, removing duplicate slashes at the
 * join point.
 */
fun combineURLs(baseURL: String, relativeURL: String): String {
    if (relativeURL.isEmpty()) return baseURL
    return baseURL.trimEnd('/') + "/" + relativeURL.trimStart('/')
}

/**
 * Resolves the final request URL from [baseURL] and [requestedURL].
 *
 * When [allowAbsoluteUrls] is `false`, absolute request URLs are also joined
 * to [baseURL], matching axios.
 */
fun buildFullPath(
    baseURL: String?,
    requestedURL: String?,
    allowAbsoluteUrls: Boolean = true,
): String {
    val requested = requestedURL ?: ""
    val relative = !isAbsoluteURL(requested)
    return if (baseURL != null && (relative || !allowAbsoluteUrls)) {
        combineURLs(baseURL, requested)
    } else {
        requested
    }
}

/**
 * Appends [params] to [url].
 */
fun buildURL(
    url: String?,
    params: Any?,
    paramsSerializer: ParamsSerializer? = null,
    options: FormSerializerOptions = FormSerializerOptions(),
): String {
    if (params == null) return url ?: ""
    var result = url ?: ""

    val serialized = when {
        paramsSerializer != null -> paramsSerializer.serialize(params, options)
        params is URLSearchParams -> params.toString()
        params is String -> params
        else -> serializeFormEntries(params, options)
            .joinToString("&") { (name, value) ->
                "${encodeQueryComponent(name)}=${encodeQueryComponent(value)}"
            }
    }

    if (serialized.isEmpty()) return result

    val hashIndex = result.indexOf('#')
    if (hashIndex != -1) {
        result = result.substring(0, hashIndex)
    }
    result += (if (result.indexOf('?') == -1) "?" else "&") + serialized
    return result
}

/**
 * Percent-encodes a query component using encodeURIComponent rules, then
 * restores `:`, `$`, `,` and encodes spaces as `+`, matching axios.
 */
fun encodeQueryComponent(value: String): String {
    val bytes = value.encodeToByteArray()
    val sb = StringBuilder(bytes.size * 3)
    val hex = "0123456789ABCDEF"
    for (byte in bytes) {
        val code = byte.toInt() and 0xFF
        val char = code.toChar()
        val unreserved = char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' ||
            char == '-' || char == '_' || char == '.' || char == '!' || char == '~' ||
            char == '*' || char == '\'' || char == '(' || char == ')'
        if (unreserved) {
            sb.append(char)
        } else {
            sb.append('%')
            sb.append(hex[code shr 4])
            sb.append(hex[code and 0x0F])
        }
    }
    return sb.toString()
        .replace("%3A", ":")
        .replace("%24", "$")
        .replace("%2C", ",")
        .replace("%20", "+")
}

/**
 * Encodes a form component using the stricter form-urlencoded rules used for
 * request bodies and [URLSearchParams].
 */
internal fun encodeFormComponent(value: String): String {
    val bytes = value.encodeToByteArray()
    val sb = StringBuilder(bytes.size * 3)
    val hex = "0123456789ABCDEF"
    for (byte in bytes) {
        val code = byte.toInt() and 0xFF
        val char = code.toChar()
        val unreserved = char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' ||
            char == '-' || char == '_' || char == '.' || char == '!' || char == '~' ||
            char == '*' || char == '\'' || char == '(' || char == ')'
        if (unreserved) {
            when (char) {
                '!' -> sb.append("%21")
                '\'' -> sb.append("%27")
                '(' -> sb.append("%28")
                ')' -> sb.append("%29")
                '~' -> sb.append("%7E")
                else -> sb.append(char)
            }
        } else {
            sb.append('%')
            sb.append(hex[code shr 4])
            sb.append(hex[code and 0x0F])
        }
    }
    return sb.toString().replace("%20", "+")
}
