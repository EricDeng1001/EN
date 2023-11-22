package model


interface PerformanceService {
    suspend fun calculate(id: DataId)

}