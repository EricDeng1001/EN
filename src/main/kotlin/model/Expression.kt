package model

import kotlinx.serialization.Serializable

typealias TimeLength = Int
typealias ArgName = String

@Serializable
data class Expression(
    val inputs: List<DataId>,
    var outputs: List<DataId>,
    val funcId: FuncId,
    val dataflow: String,
    val arguments: Map<ArgName, Argument>
) {
    fun isRoot(): Boolean {
        return this.inputs.isEmpty()
    }

    companion object {
        fun makeRoot(id: DataId): Expression = Expression(
            inputs = emptyList(),
            outputs = listOf(id),
            funcId = FuncId.Noop,
            dataflow = "",
            arguments = emptyMap()
        )
    }
}


