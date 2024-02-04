package infra.executor.ktorclient

import model.*
import model.executor.axis.Period
import model.executor.axis.TimeRange
import model.executor.data.*
import model.executor.task.Callback
import model.executor.task.ExpressionTask
import model.executor.task.ExpressionTaskRepository
import model.executor.task.Status
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class BuiltinExecutor(
    val symbolLibraryService: SymbolLibraryService,
    val expressionTaskRepository: ExpressionTaskRepository
) : Executor {

    private val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    class SaveExpressionTaskCallback(
        val expressionTask: ExpressionTask, val expressionTaskRepository:
        ExpressionTaskRepository
    ) :
        Callback<Status>(permanent = true) {
        override fun call(data: Status) {
            expressionTaskRepository.save(expressionTask)
        }
    }

    suspend fun findData(dataIds: List<DataId>): List<Data> {
        val datas = mutableListOf<Data>()
        for (dataId in dataIds) {
            val symbol = symbolLibraryService.getSymbol(SymbolId(dataId.str))
            val data = Data(
                DataMeta(
                    name = symbol.id.str,
                    axis = symbol.axis,
                    period = Period.valueOf(symbol.freq),
                    offset = symbol.offsetValue
                )
            )
            datas.add(data)
        }
        return datas
    }

    override suspend fun run(task: Task): Boolean {
        try {
            val inputs = mutableListOf<InputsItem>()
            for (input in task.expression.inputs) {
                val datas = findData(input.ids)
                inputs.add(TimeBaseInput(input.type, datas))
            }
            val outputs = findData(task.expression.outputs)
            val timeRange = TimeRange.makeRange(
                start = task.from.value,
                end = task.to.value,
                period = outputs[0].meta.period,
                offset = outputs[0].meta.offset,
            )
            val et = ExpressionTask(
                taskId = task.id,
                funcId = task.expression.funcId.value,
                inputs = inputs,
                outputs = outputs,
                timeRange = timeRange,
                arguments = task.expression.arguments,
                priority = task.priority
            )
            et.addStatusCallback(
                SaveExpressionTaskCallback(et, this.expressionTaskRepository),
                *Status.entries.toTypedArray()
            )
            // add en hook
            et.run()
        } catch (e: Exception) {
            logger.error("Error running task ${task.id}", e)
            return false
        }
        return true
    }

    override suspend fun tryCancel(id: TaskId) {
        TODO("Not yet implemented")
    }
}