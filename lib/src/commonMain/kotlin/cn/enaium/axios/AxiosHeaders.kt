package cn.enaium.axios

/**
 * Case-insensitive HTTP header container with axios-compatible semantics.
 *
 * Header names are normalized to lower case, values are stored as lists so
 * repeated headers (for example `Set-Cookie`) are preserved.
 */
class AxiosHeaders(initial: Map<String, Any?>? = null) {

    private val values = LinkedHashMap<String, MutableList<String>>()
    private val displayNames = LinkedHashMap<String, String>()

    init {
        initial?.let { set(it) }
    }

    val size: Int get() = values.size

    val isEmpty: Boolean get() = values.isEmpty()

    /** Snapshot of the stored entries, using lower-cased header names. */
    val entries: List<Pair<String, List<String>>>
        get() = values.entries.map { (key, headerValues) ->
            (displayNames[key] ?: key) to headerValues.toList()
        }

    operator fun get(name: String): String? = values[normalizeName(name)]?.firstOrNull()

    fun getAll(name: String): List<String> = values[normalizeName(name)]?.toList().orEmpty()

    operator fun set(name: String, value: Any?) {
        set(name, value, true)
    }

    /**
     * Sets a header. A `null` or `false` value removes the header, matching the
     * way axios treats disabled headers.
     *
     * @param rewrite when `false`, an existing value is kept.
     */
    fun set(name: String, value: Any?, rewrite: Boolean): AxiosHeaders {
        val key = validateName(name)
        if (value == null || value == false) {
            if (rewrite) {
                values.remove(key)
                displayNames.remove(key)
            }
            return this
        }

        val normalized = normalizeValue(value)
        if (normalized.isEmpty()) {
            if (rewrite) {
                values.remove(key)
                displayNames.remove(key)
            }
            return this
        }

        if (!rewrite && values.containsKey(key)) {
            return this
        }

        values[key] = normalized.toMutableList()
        if (!displayNames.containsKey(key)) displayNames[key] = name.trim()
        return this
    }

    fun set(name: String, values: Iterable<Any?>, rewrite: Boolean = true): AxiosHeaders {
        val key = validateName(name)
        val normalized = values
            .filter { it != null && it != false }
            .flatMap { normalizeValue(it!!) }
        if (normalized.isEmpty()) {
            if (rewrite) {
                this.values.remove(key)
                displayNames.remove(key)
            }
            return this
        }
        if (!rewrite && this.values.containsKey(key)) {
            return this
        }
        this.values[key] = normalized.toMutableList()
        if (!displayNames.containsKey(key)) displayNames[key] = name.trim()
        return this
    }

    fun set(headers: AxiosHeaders, rewrite: Boolean = true): AxiosHeaders {
        headers.entries.forEach { (name, headerValues) ->
            set(name, headerValues, rewrite)
        }
        return this
    }

    fun set(headers: Map<String, Any?>, rewrite: Boolean = true): AxiosHeaders {
        headers.forEach { (name, value) -> set(name, value, rewrite) }
        return this
    }

    fun set(headers: Iterable<Pair<String, Any?>>, rewrite: Boolean = true): AxiosHeaders {
        // Preserve repeated keys by grouping before applying the rewrite policy.
        val grouped = LinkedHashMap<String, MutableList<Any?>>()
        headers.forEach { (name, value) ->
            grouped.getOrPut(name) { mutableListOf() }.add(value)
        }
        grouped.forEach { (name, headerValues) ->
            val flat = headerValues.flatMap { value ->
                when (value) {
                    is Iterable<*> -> value.toList()
                    else -> listOf(value)
                }
            }
            set(name, flat, rewrite)
        }
        return this
    }

    operator fun contains(name: String): Boolean = has(name)

    fun has(name: String): Boolean {
        val key = normalizeName(name)
        return key.isNotEmpty() && values[key]?.isNotEmpty() == true
    }

    fun has(name: String, value: String, ignoreCase: Boolean = false): Boolean =
        values[normalizeName(name)]?.any { it.equals(value, ignoreCase) } == true

    fun delete(name: String): Boolean {
        val key = normalizeName(name)
        displayNames.remove(key)
        return values.remove(key) != null
    }

    fun delete(names: Iterable<String>): Boolean {
        var deleted = false
        names.forEach { if (delete(it)) deleted = true }
        return deleted
    }

    fun clear(): Boolean {
        if (values.isEmpty()) return false
        values.clear()
        displayNames.clear()
        return true
    }

    /**
     * Canonicalizes header names, for example `content-type` to `Content-Type`.
     * With [format] disabled the names merely stay trimmed and lower-cased.
     */
    fun normalize(format: Boolean = true): AxiosHeaders {
        values.keys.toList().forEach { key ->
            displayNames[key] = if (format) formatHeader(key) else key
        }
        return this
    }

    fun concat(vararg targets: Any?): AxiosHeaders {
        val result = copy()
        targets.forEach { target ->
            when (target) {
                null -> Unit
                is AxiosHeaders -> result.set(target)
                is Map<*, *> -> target.forEach { (key, value) ->
                    if (key != null) result.set(key.toString(), value)
                }
                is Iterable<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    result.set(target.map { it as Pair<String, Any?> })
                }
                else -> error("Unsupported header target: ${target::class.simpleName}")
            }
        }
        return result
    }

    fun copy(): AxiosHeaders = AxiosHeaders().also { copy ->
        values.forEach { (name, headerValues) -> copy.values[name] = headerValues.toMutableList() }
        displayNames.forEach { (name, display) -> copy.displayNames[name] = display }
    }

    fun toJSON(asStrings: Boolean = true): Map<String, Any> {
        val result = LinkedHashMap<String, Any>()
        values.forEach { (name, headerValues) ->
            if (headerValues.isEmpty()) return@forEach
            val displayName = displayNames[name] ?: name
            result[displayName] = if (asStrings) headerValues.joinToString(", ") else headerValues.toList()
        }
        return result
    }

    fun getSetCookie(): List<String> = getAll("set-cookie")

    fun setContentType(value: Any?, rewrite: Boolean = true): AxiosHeaders =
        set("Content-Type", value, rewrite)

    fun getContentType(): String? = get("Content-Type")

    fun hasContentType(): Boolean = has("Content-Type")

    fun setAccept(value: Any?, rewrite: Boolean = true): AxiosHeaders =
        set("Accept", value, rewrite)

    fun getAccept(): String? = get("Accept")

    fun setAuthorization(value: Any?, rewrite: Boolean = true): AxiosHeaders =
        set("Authorization", value, rewrite)

    fun getAuthorization(): String? = get("Authorization")

    fun setUserAgent(value: Any?, rewrite: Boolean = true): AxiosHeaders =
        set("User-Agent", value, rewrite)

    override fun toString(): String =
        entries.joinToString("\n") { (name, headerValues) -> "$name: ${headerValues.joinToString(", ")}" }

    private fun normalizeName(name: String): String = name.trim().lowercase()

    private fun validateName(name: String): String {
        val normalized = normalizeName(name)
        require(normalized.isNotEmpty()) { "Header name must not be blank" }
        require(!normalized.contains(':') && !normalized.containsAny('\r', '\n')) {
            "Invalid header name: $name"
        }
        return normalized
    }

    private fun normalizeValue(value: Any): List<String> = when (value) {
        is Iterable<*> -> value.filterNotNull().map { sanitizeHeaderValue(it.toString()) }
        is Array<*> -> value.filterNotNull().map { sanitizeHeaderValue(it.toString()) }
        else -> listOf(sanitizeHeaderValue(value.toString()))
    }

    companion object {
        fun from(thing: Any?): AxiosHeaders = when (thing) {
            null -> AxiosHeaders()
            is AxiosHeaders -> thing
            is Map<*, *> -> AxiosHeaders(
                thing.entries
                    .filter { it.key != null }
                    .associate { it.key.toString() to it.value }
            )
            is String -> AxiosHeaders(parseHeaderLines(thing))
            else -> error("Unsupported headers source: ${thing::class.simpleName}")
        }

        fun concat(vararg targets: Any?): AxiosHeaders {
            val result = AxiosHeaders()
            targets.forEach { target ->
                when (target) {
                    null -> Unit
                    is AxiosHeaders -> result.set(target)
                    is Map<*, *> -> target.forEach { (key, value) ->
                        if (key != null) result.set(key.toString(), value)
                    }
                    is Iterable<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        result.set(target.map { it as Pair<String, Any?> })
                    }
                    is String -> result.set(parseHeaderLines(target))
                    else -> error("Unsupported headers target: ${target::class.simpleName}")
                }
            }
            return result
        }

        fun parseParameters(value: String): Map<String, String> {
            val result = LinkedHashMap<String, String>()
            splitParameters(value).forEach { parameter ->
                val separator = parameter.indexOf('=')
                if (separator <= 0) return@forEach
                val name = parameter.substring(0, separator).trim().lowercase()
                val raw = parameter.substring(separator + 1).trim()
                val decoded = if (raw.length >= 2 && raw.startsWith('"') && raw.endsWith('"')) {
                    raw.substring(1, raw.length - 1).replace("\\\"", "\"").replace("\\\\", "\\")
                } else {
                    raw
                }
                if (name.isNotEmpty()) result[name] = decoded
            }
            return result
        }

        private fun parseHeaderLines(raw: String): Map<String, Any?> {
            val result = LinkedHashMap<String, Any?>()
            raw.split('\n').forEach { line ->
                val separator = line.indexOf(':')
                if (separator <= 0) return@forEach
                val name = line.substring(0, separator).trim()
                val value = line.substring(separator + 1).trim()
                if (name.isEmpty()) return@forEach
                val key = name.lowercase()
                if (key == "set-cookie") {
                    val previous = result[key]
                    result[key] = when (previous) {
                        null -> listOf(value)
                        is List<*> -> previous + value
                        else -> listOf(previous, value)
                    }
                } else {
                    val previous = result[key]
                    result[key] = if (previous == null) value else "$previous, $value"
                }
            }
            return result
        }

        private fun splitParameters(value: String): List<String> {
            val parts = mutableListOf<String>()
            val current = StringBuilder()
            var quoted = false
            var escaped = false
            value.forEach { char ->
                when {
                    escaped -> {
                        current.append(char)
                        escaped = false
                    }
                    char == '\\' && quoted -> {
                        current.append(char)
                        escaped = true
                    }
                    char == '"' -> {
                        current.append(char)
                        quoted = !quoted
                    }
                    (char == ',' || char == ';') && !quoted -> {
                        parts += current.toString()
                        current.clear()
                    }
                    else -> current.append(char)
                }
            }
            parts += current.toString()
            return parts
        }

        internal fun formatHeader(header: String): String =
            header.trim().split('-').joinToString("-") { part ->
                part.lowercase().replaceFirstChar { it.uppercase() }
            }

        internal fun sanitizeHeaderValue(value: String): String =
            value
                .replace(Regex("[\\u0000-\\u0008\\u000a-\\u001f\\u007f]+"), "")
                .trim(' ', '\t')
    }
}

private fun String.containsAny(vararg chars: Char): Boolean = chars.any { contains(it) }
