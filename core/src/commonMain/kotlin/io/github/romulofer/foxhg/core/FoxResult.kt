package io.github.romulofer.foxhg.core

/** Result of parsing or loading ROM data. Malformed input is an [Err], never an uncaught exception in the UI. */
sealed class FoxResult<out T> {
    data class Ok<T>(val value: T) : FoxResult<T>()
    data class Err(val message: String) : FoxResult<Nothing>()

    fun getOrNull(): T? = (this as? Ok)?.value
}
