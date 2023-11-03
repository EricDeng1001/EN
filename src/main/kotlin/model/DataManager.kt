package model

interface DataManager {
    fun findLastPtr(id: DataId): Pointer
}
