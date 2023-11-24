package model

interface MessageQueue {
    suspend fun pushRunning(id: DataId)

    suspend fun pushRunFailed(id: DataId)

    suspend fun pushRunFinish(id: DataId)

    suspend fun pushSystemFailed(id: DataId)

}