package model

interface NodeRepository {
    fun save(node: Node): Node
    fun queryByExpression(expression: Expression): ExpressionNode
    fun queryByInput(id: DataId): ExpressionNode

    fun queryByOutput(id: DataId): ExpressionNode
    fun queryRoot(id: DataId): RootNode

}