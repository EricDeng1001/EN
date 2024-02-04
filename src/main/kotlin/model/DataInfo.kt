package model


interface DataInfo {
    suspend fun getExpressDataInfo(id: DataId, start: String?, end: String?): String
}

