package model


interface PerformanceService {
    suspend fun calculate(id: SymbolId)

}