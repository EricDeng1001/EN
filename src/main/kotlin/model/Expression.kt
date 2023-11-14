package model

import kotlinx.serialization.Serializable

typealias TimeLength = Int
typealias ArgName = String

@Serializable
data class Symbol(
    val id: DataId,
    val type: String,
    val offset: String,
    val freq: String
)

@Serializable
data class Expression(
    val inputs: List<DataId>,
    var outputs: List<Symbol>,
    val funcId: FuncId,
    val dataflow: String,
    val arguments: Map<ArgName, Argument>
) {
    fun isRoot(): Boolean = this.inputs.isEmpty()

    companion object {
        fun makeRoot(symbol: Symbol): Expression = Expression(
            inputs = emptyList(),
            outputs = listOf(symbol),
            funcId = FuncId.Noop,
            dataflow = "",
            arguments = emptyMap()
        )
    }
}


