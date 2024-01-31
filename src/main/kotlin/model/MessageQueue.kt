package model

interface MessageQueue {
    suspend fun pushRunning(id: DataId)

    suspend fun pushRunFailed(id: DataId, reason: String)

    suspend fun pushRunFinish(id: DataId, effectivePtr: Pointer)

    suspend fun pushSystemFailed(id: DataId)

}