package com.hermes.agent.core.common.util

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * Converts a Flow<T> into a Flow<HermesResult<T>> with proper loading/success/error states.
 */
fun <T> Flow<T>.asResult(): Flow<HermesResult<T>> = this
    .map<T, HermesResult<T>> { HermesResult.Success(it) }
    .catch { emit(HermesResult.Failure(HermesError.fromException(it))) }

/**
 * Represents UI state that can be Loading, Success, or Error.
 */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>

    val isLoading: Boolean get() = this is Loading
    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error

    fun getOrNull(): T? = (this as? Success)?.data
}

/**
 * Convert HermesResult to UiState.
 */
fun <T> HermesResult<T>.toUiState(): UiState<T> = when (this) {
    is HermesResult.Success -> UiState.Success(data)
    is HermesResult.Failure -> UiState.Error(error.message)
}
