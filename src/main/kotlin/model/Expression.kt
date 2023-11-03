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

    }

    @Serializable
    data class AlignmentRule(val offsets: Map<DataId, TimeLength>) {
    }

    fun isRoot(): Boolean {
        return this.inputs.isEmpty()
    }

}


