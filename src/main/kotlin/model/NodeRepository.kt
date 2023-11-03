package model

interface NodeRepository {
    fun save(node: Node): Node
    fun queryByExpression(expression: Expression): Node
    fun queryByInput(id: DataId): Node

    fun queryByOutput(id: DataId): Node
    fun queryByFunc(): Node

}