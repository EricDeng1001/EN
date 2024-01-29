package model

import kotlinx.serialization.Serializable


interface SymbolLibraryService {
    suspend fun getSymbol(symbolId: SymbolId): Symbol
}


@Serializable
enum class Frequency(val frequencyValue: Int) {
    T(1),
    M(20),
    D(4840);
}

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

