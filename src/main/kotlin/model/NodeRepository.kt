package model

interface NodeRepository {
    fun save(node: Node): Node
    fun queryByExpression(expression: Expression): Node?
    fun queryByInput(id: DataId): Set<Node>
    fun queryByOutput(id: DataId): Node?
    fun queryByFunc(funcId: FuncId): Set<Node>

    fun queryIdByOutput(id: DataId): Node.Id?

}