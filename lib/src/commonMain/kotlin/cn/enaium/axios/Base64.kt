package cn.enaium.axios

private const val BASE64_ALPHABET =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

internal fun base64Encode(bytes: ByteArray): String {
    val result = StringBuilder((bytes.size + 2) / 3 * 4)
    var index = 0
    while (index < bytes.size) {
        val b0 = bytes[index].toInt() and 0xFF
        val b1 = if (index + 1 < bytes.size) bytes[index + 1].toInt() and 0xFF else 0
        val b2 = if (index + 2 < bytes.size) bytes[index + 2].toInt() and 0xFF else 0

        result.append(BASE64_ALPHABET[b0 shr 2])
        result.append(BASE64_ALPHABET[((b0 and 0x03) shl 4) or (b1 shr 4)])
        if (index + 1 < bytes.size) {
            result.append(BASE64_ALPHABET[((b1 and 0x0F) shl 2) or (b2 shr 6)])
        } else {
            result.append('=')
        }
        if (index + 2 < bytes.size) {
            result.append(BASE64_ALPHABET[b2 and 0x3F])
        } else {
            result.append('=')
        }
        index += 3
    }
    return result.toString()
}

internal fun base64Encode(value: String): String = base64Encode(value.encodeToByteArray())

internal fun basicAuth(username: String, password: String): String =
    "Basic " + base64Encode("$username:$password")
