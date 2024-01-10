package model

import kotlinx.serialization.Serializable


interface SymbolLibraryService {
    suspend fun getSymbol(symbolId: SymbolId): Symbol
}


@Serializable
enum class Frequency(val frequencyValue: Int) {
    M(20),
    D(4840);
}


@JvmInline
@Serializable
value class SymbolId(val str: String)

@Serializable
data class Symbol(
    val id: SymbolId,
    val axis: String,
    val offset: String,
    val freq: String
) {
    val offsetValue: Int by lazy { offset.toIntOrNull() ?: error("Invalid offset: $offset") }
    val frequencyValue: Int by lazy { Frequency.valueOf(freq).frequencyValue }
}

