package com.hermes.agent.core.common.util

import kotlin.UnsafeVariance

/**
 * A sealed interface representing the result of an operation.
 * Similar to Kotlin's Result but with explicit Success/Failure types for clarity.
 */
sealed interface HermesResult<out T> {
    data class Success<T>(val data: T) : HermesResult<T>
    data class Failure(val error: HermesError) : HermesResult<Nothing>

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure

    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Failure -> null
    }

    fun getOrDefault(default: @UnsafeVariance T): T = when (this) {
        is Success -> data
        is Failure -> default
    }

    fun exceptionOrNull(): HermesError? = when (this) {
        is Success -> null
        is Failure -> error
    }

    fun <R> map(transform: (T) -> R): HermesResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Failure -> this
    }

    fun <R> mapCatching(transform: (T) -> R): HermesResult<R> = try {
        map(transform)
    } catch (e: Exception) {
        Failure(HermesError.fromException(e))
    }

    fun onSuccess(action: (@UnsafeVariance T) -> Unit): HermesResult<T> {
        if (this is Success) action(data)
        return this
    }

    fun onFailure(action: (HermesError) -> Unit): HermesResult<T> {
        if (this is Failure) action(error)
        return this
    }
}

/**
 * Represents a structured error with code, message, and optional cause.
 */
data class HermesError(
    val code: String,
    val message: String,
    val cause: Throwable? = null
) {
    companion object {
        fun fromException(e: Throwable): HermesError = HermesError(
            code = e.javaClass.simpleName ?: "Unknown",
            message = e.message ?: "Unknown error",
            cause = e
        )

        val NetworkError = HermesError("NETWORK", "Network error occurred")
        val TimeoutError = HermesError("TIMEOUT", "Request timed out")
        val AuthError = HermesError("AUTH", "Authentication failed")
        val NotFoundError = HermesError("NOT_FOUND", "Resource not found")
        val RateLimitError = HermesError("RATE_LIMIT", "Rate limit exceeded")
        val UnknownError = HermesError("UNKNOWN", "An unknown error occurred")
    }
}

// Convenience inline functions
inline fun <T> runCatchingResult(block: () -> T): HermesResult<T> = try {
    HermesResult.Success(block())
} catch (e: Exception) {
    HermesResult.Failure(HermesError.fromException(e))
}

suspend inline fun <T> suspendCatchingResult(crossinline block: suspend () -> T): HermesResult<T> = try {
    HermesResult.Success(block())
} catch (e: Exception) {
    HermesResult.Failure(HermesError.fromException(e))
}
