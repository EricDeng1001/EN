package model


interface Worker {
    suspend fun getExpressDataInfo(id: DataId, start: String?, end: String?): String
}

