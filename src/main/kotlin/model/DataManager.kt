package model

interface DataManager {
    suspend fun findLastPtr(id: DataId): Pointer
}
