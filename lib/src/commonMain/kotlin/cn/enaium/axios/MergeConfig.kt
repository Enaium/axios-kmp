package cn.enaium.axios

/**
 * Merges [config2] over [config1]; `null` values in [config2] inherit from
 * [config1]. Headers are concatenated case-insensitively.
 */
fun mergeConfig(
    config1: AxiosRequestConfig,
    config2: AxiosRequestConfig,
): AxiosRequestConfig {
    val result = config1.copy()

    config2.url?.let { result.url = it }
    config2.method?.let { result.method = it.lowercase() }
    config2.baseURL?.let { result.baseURL = it }
    config2.allowAbsoluteUrls?.let { result.allowAbsoluteUrls = it }
    config2.transformRequest?.let { result.transformRequest = it }
    config2.transformResponse?.let { result.transformResponse = it }
    result.headers = when {
        config1.headers == null && config2.headers == null -> null
        config1.headers == null -> config2.headers?.copy()
        config2.headers == null -> config1.headers?.copy()
        else -> AxiosHeaders.concat(config1.headers, config2.headers)
    }
    result.params = mergeParams(config1.params, config2.params)
    config2.paramsSerializer?.let { result.paramsSerializer = it }
    if (config2.data != null) result.data = config2.data
    config2.timeout?.let { result.timeout = it }
    config2.timeoutErrorMessage?.let { result.timeoutErrorMessage = it }
    config2.withCredentials?.let { result.withCredentials = it }
    config2.adapter?.let { result.adapter = it }
    config2.auth?.let { result.auth = it }
    config2.responseType?.let { result.responseType = it }
    config2.responseEncoding?.let { result.responseEncoding = it }
    config2.xsrfCookieName?.let { result.xsrfCookieName = it }
    config2.xsrfHeaderName?.let { result.xsrfHeaderName = it }
    config2.withXSRFToken?.let { result.withXSRFToken = it }
    config2.onUploadProgress?.let { result.onUploadProgress = it }
    config2.onDownloadProgress?.let { result.onDownloadProgress = it }
    config2.maxContentLength?.let { result.maxContentLength = it }
    config2.maxBodyLength?.let { result.maxBodyLength = it }
    config2.maxRedirects?.let { result.maxRedirects = it }
    config2.validateStatus?.let { result.validateStatus = it }
    config2.cancelToken?.let { result.cancelToken = it }
    config2.decompress?.let { result.decompress = it }
    result.transitional = when (val transitional = config2.transitional) {
        null -> config1.transitional
        else -> transitional.resolve(config1.transitional ?: DefaultTransitionalOptions)
    }
    config2.proxy?.let { result.proxy = it }
    config2.formSerializer?.let { result.formSerializer = it }
    config2.redact?.let { result.redact = it }
    config2.sensitiveHeaders?.let { result.sensitiveHeaders = it }
    config2.json?.let { result.json = it }

    return result
}
