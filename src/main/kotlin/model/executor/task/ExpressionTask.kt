package model.executor.task

import kotlinx.serialization.Serializable
import model.ArgName
import model.Argument
import model.executor.AtomicBooleanSerializer
import model.executor.axis.TimeAxisMappingRuleTable
import model.executor.axis.TimeRange
import model.executor.data.Inputs
import model.executor.data.Outputs
import java.util.concurrent.atomic.AtomicBoolean

@Serializable
class ExpressionTask(
    val taskId: String,
    val funcId: String,
    val inputs: Inputs,
    val outputs: Outputs,
    val timeRange: TimeRange,
    val arguments: Map<ArgName, Argument>,
    val priority: Int
) : StatusResponsive(taskId), Stateful {
    var tasks: MutableList<WorkTask>? = null
    var error: String? = null

    @Serializable(with = AtomicBooleanSerializer::class)
    val stopped: AtomicBoolean = AtomicBoolean(false)

    init {
        TimeAxisMappingRuleTable.generateMappings(inputs, outputs, arguments)
    }

    fun run() {
        if (stopped.get()) {
            throw IllegalStateException("$taskId task is stopped")
        }
        if (tasks != null) {
            throw IllegalStateException("$taskId task has been run")
        }
        statusChanged(Status.pending)



        // TODO
    }

    override fun status(): Status {
        if (this.error != null) {
            return Status.failed
        }
        if (this.stopped.get()) {
            return Status.failed
        }
        if (this.tasks == null) {
            return Status.inited
        }
        return Stateful.mergeStatus(this.tasks!!)
    }
}

interface ExpressionTaskRepository {
    fun save(task: ExpressionTask)
    fun find(taskId: String): ExpressionTask
    fun delete(taskId: String)
}