package model


interface DataInfo {
    suspend fun getExpressDataInfo(id: DataId, start: String?, end: String?, needPerf: String?): String
    suspend fun deleteData(id: DataId)
}

