package model.executor.task

import kotlinx.serialization.Serializable
import model.ArgName
import model.Argument
import model.executor.axis.TimeAxisMapping
import model.executor.axis.TimeAxisMappingRuleTable
import model.executor.axis.TimeRange
import model.executor.data.Inputs
import model.executor.data.Outputs

@Serializable
class ExpressionTask(
    val taskId: String,
    val funcId: String,
    val inputs: Inputs,
    val outputs: Outputs,
    val timeRange: TimeRange,
    val arguments: Map<ArgName, Argument>
) {
    val timeAxisMapping: List<TimeAxisMapping>

    init {
        timeAxisMapping = TimeAxisMappingRuleTable.generateMappings(inputs, outputs, arguments)

    }
}