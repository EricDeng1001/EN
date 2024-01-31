package model

interface NodeRepository {
    suspend fun save(node: Node): Node
    // ignore outputs
    suspend fun queryByExpression(expression: Expression): Node?
    suspend fun queryByInput(id: DataId): List<Node>
    suspend fun queryByOutput(id: DataId): Node?
    suspend fun queryByFunc(funcId: FuncId): List<Node>

    suspend fun queryByShouldUpdate(shouldUpdate: Boolean): List<Node>

    suspend fun queryAllRoot(): List<Node>
    suspend fun queryAllNonRoot(): List<Node>
    suspend fun saveAll(nodes: Iterable<Node>)
    suspend fun get(id: NodeId): Node?
    suspend fun logicDelete(ids: List<NodeId>): Long

}