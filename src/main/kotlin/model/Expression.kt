package model

import kotlinx.serialization.Serializable

typealias TimeLength = Int
typealias ArgName = String

@Serializable
data class Expression(
    val inputs: List<DataId>,
    var outputs: List<DataId>,
    val funcId: FuncId,
    val shapeRule: ShapeRule,
    val alignmentRule: AlignmentRule,
    val arguments: Map<ArgName, Argument>
) {
    @Serializable
    data class ShapeRule(val m: Int, val n: Int) {
        companion object {
            val Noop = ShapeRule(0, 0)
        }
    }

    @Serializable
    data class AlignmentRule(val offsets: Map<DataId, TimeLength>) {
        companion object {
            val Noop = AlignmentRule(emptyMap())
        }
    }

    fun isRoot(): Boolean {
        return this.inputs.isEmpty()
    }

    companion object {
        fun makeRoot(id: DataId): Expression = Expression(
            inputs = emptyList(),
            outputs = listOf(id),
            funcId = FuncId.Noop,
            shapeRule = ShapeRule.Noop,
            AlignmentRule.Noop,
            arguments = emptyMap()
        )
    }
}


