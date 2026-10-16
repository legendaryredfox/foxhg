package io.github.legendaryredfox.foxhg.core

/** Result of parsing or loading ROM data. Malformed input is an [Err], never an uncaught exception in the UI. */
sealed class FoxResult<out T> {
    data class Ok<T>(val value: T) : FoxResult<T>()
    data class Err(val message: String) : FoxResult<Nothing>()

    fun getOrNull(): T? = (this as? Ok)?.value
}

/**
 * Runs [block], a parser of bytes that may be damaged or hand-edited: a read past the end, a negative size or a failed check inside it
 * becomes an [FoxResult.Err] saying [what] is damaged, not an exception that takes the screen down.
 */
inline fun <T> parsing(what: String, block: () -> FoxResult<T>): FoxResult<T> = try {
    block()
} catch (e: IndexOutOfBoundsException) {
    FoxResult.Err("$what is damaged: it reads past its own data")
} catch (e: IllegalArgumentException) {
    FoxResult.Err("$what is damaged: ${e.message}")
} catch (e: NegativeArraySizeException) {
    FoxResult.Err("$what is damaged: a size is negative")
}
