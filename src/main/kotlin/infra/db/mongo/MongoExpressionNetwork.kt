package infra.db.mongo

import model.*

object MongoExpressionNetwork : ExpressionNetwork(
    MongoNodeRepository,
    MongoTaskRepository,
    object : DataManager {
        override fun findLastPtr(id: DataId): Pointer {
            TODO("Not yet implemented")
        }
    },
    object : Executor {
        override fun run(expression: Expression, from: Pointer, to: Pointer, withId: TaskId) {
            TODO("Not yet implemented")
        }

        override fun tryCancel(id: TaskId) {
            TODO("Not yet implemented")
        }
    }) {

}