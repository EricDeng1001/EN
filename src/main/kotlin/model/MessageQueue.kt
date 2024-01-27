package model

interface MessageQueue {
    suspend fun pushRunning(id: SymbolId)

    suspend fun pushRunFailed(id: SymbolId, reason: String)

    suspend fun pushRunFinish(id: SymbolId)

    suspend fun pushSystemFailed(id: SymbolId)

}