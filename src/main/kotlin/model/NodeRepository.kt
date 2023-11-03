package model

interface NodeRepository {
    fun save(node: Node): Node
    fun queryByExpression(expression: Expression): Node?
    fun queryByInput(id: DataId): Set<Node>
    fun queryByOutput(id: DataId): Node?
    fun queryByFunc(funcId: FuncId): Node?

    fun lockNode(node: Node)

    fun unlockNode(node: Node)
}