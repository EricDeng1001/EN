package model.executor.task

import kotlinx.serialization.Serializable
import model.ArgName
import model.Argument
import model.executor.axis.TimeRange
import model.executor.data.InputsRef
import model.executor.data.Outputs

@Serializable
abstract class WorkTask(
    val taskId: String,
    val funcId: String,
    val inputs: InputsRef,
    val outputs: Outputs,
    val timeRange: TimeRange,
    val arguments: Map<ArgName, Argument>,
) : StatusResponsive(taskId), Stateful {

    override fun status(): Status {
        TODO("Not yet implemented")
    }

    abstract fun run()
}