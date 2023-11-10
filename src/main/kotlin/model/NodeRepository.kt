package model

interface NodeRepository {
    suspend fun save(node: Node): Node
    // ignore outputs
    suspend fun queryByExpression(expression: Expression): Node?
    suspend fun queryByInput(id: DataId): Set<Node>
    suspend fun queryByOutput(id: DataId): Node?
    suspend fun queryByFunc(funcId: FuncId): Set<Node>

}