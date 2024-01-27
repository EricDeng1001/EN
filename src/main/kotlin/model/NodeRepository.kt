package model

interface NodeRepository {
    suspend fun save(node: Node): Node
    // ignore outputs
    suspend fun queryByExpression(expression: Expression): Node?

    suspend fun downstream1Lvl(node: Node): Iterable<Node>

    suspend fun upstream1Lvl(node: Node): Iterable<Node>
    suspend fun queryByInput(id: SymbolId): List<Node>
    suspend fun queryByOutput(id: SymbolId): Node?
    suspend fun queryByFunc(funcId: FuncId): List<Node>

    suspend fun queryAllRoot(): List<Node>
    suspend fun queryAllNonRoot(): List<Node>
    suspend fun saveAll(nodes: Iterable<Node>)
    suspend fun get(id: NodeId): Node?

    suspend fun queryByInput(id: List<SymbolId>): List<Node>
    suspend fun queryByOutput(id: List<SymbolId>): List<Node>
}