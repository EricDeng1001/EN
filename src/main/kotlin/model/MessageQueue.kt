package model

interface MessageQueue {
    fun pushRunning(id: DataId)

    fun pushRunFailed(id: DataId)

    fun pushRunFinish(id: DataId)

}