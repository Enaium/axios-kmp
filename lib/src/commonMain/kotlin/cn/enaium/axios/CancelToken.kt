package cn.enaium.axios

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred

typealias Canceler = (message: String?, config: AxiosRequestConfig?, request: Any?) -> Unit

/**
 * Cancellation token compatible with axios' `CancelToken`.
 *
 * Coroutine cancellation works independently: cancelling the calling coroutine
 * cancels the underlying Ktor request without requiring a token.
 */
class CancelToken(executor: (Canceler) -> Unit) {

    private val signal = CompletableDeferred<CanceledError>()
    private val listeners = mutableListOf<(CanceledError) -> Unit>()

    var reason: CanceledError? = null
        private set

    val isCancelled: Boolean
        get() = reason != null

    init {
        executor { message, config, request -> cancel(message, config, request) }
    }

    fun cancel(
        message: String? = null,
        config: AxiosRequestConfig? = null,
        request: Any? = null,
    ) {
        if (reason != null) return
        val error = CanceledError(message, config, request)
        reason = error
        val pending = listeners.toList()
        listeners.clear()
        pending.forEach { it(error) }
        signal.complete(error)
    }

    fun throwIfRequested() {
        reason?.let { throw it }
    }

    suspend fun awaitCancellation(): CanceledError = signal.await()

    /** Deferred that completes with the cancellation reason. */
    fun toDeferred(): Deferred<CanceledError> = signal

    /**
     * Registers a listener; returns a function that unsubscribes it.
     * Listeners fire at most once.
     */
    fun subscribe(listener: (CanceledError) -> Unit): () -> Unit {
        val current = reason
        if (current != null) {
            listener(current)
            return {}
        }
        listeners += listener
        return { listeners.remove(listener) }
    }

    companion object {
        /**
         * Creates a token together with its cancel handle, mirroring
         * `CancelToken.source()`.
         */
        fun source(): CancelTokenSource = CancelTokenSource(CancelToken { })
    }
}

/**
 * Token/cancel pair returned by [CancelToken.source].
 */
class CancelTokenSource internal constructor(
    val token: CancelToken,
) {
    fun cancel(
        message: String? = null,
        config: AxiosRequestConfig? = null,
        request: Any? = null,
    ) {
        token.cancel(message, config, request)
    }
}
