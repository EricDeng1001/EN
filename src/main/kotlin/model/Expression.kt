package model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

typealias TimeLength = Int
typealias ArgName = String

enum class InputType {
    @SerialName("dataId")
    DataId,

    @SerialName("list")
    List
}

@Serializable
data class Input(val type: InputType, val ids: List<DataId>)

@Serializable
data class Expression(
    val inputs: List<Input>,
    var outputs: List<DataId>,
    val funcId: FuncId,
    val dataflow: String,
    val arguments: Map<ArgName, Argument>,
) {
    fun isRoot(): Boolean = this.inputs.isEmpty()

    companion object {
        fun makeRoot(id: DataId): Expression = Expression(
            inputs = emptyList(),
            outputs = listOf(id),
            funcId = FuncId.Noop,
            dataflow =  "",
            arguments = emptyMap()
        )
    }
}

data class BatchExpression(
    val expressions: List<Expression>
)


