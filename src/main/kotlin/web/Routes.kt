package web

import infra.db.mongo.MongoExpressionNetwork
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import model.DataId
import model.Expression
import model.FuncId
import model.TaskId

fun Route.routes() {
    post("/run") {
        val dataId = call.receive<DataId>()
        MongoExpressionNetwork.run(dataId)
        call.respond(HttpStatusCode.OK)
    }

    post("/add") {
        val expression = call.receive<Expression>()
        val ids = MongoExpressionNetwork.add(expression)
        call.respond(ids)
    }

    post("/update_func") {
        val funcId = call.receive<FuncId>()
        MongoExpressionNetwork.updateFunc(funcId)
        call.respond(HttpStatusCode.OK)
    }

    post("/succeed_run") {
        val taskId = call.receive<TaskId>()
        MongoExpressionNetwork.succeedRun(taskId)
    }

    post("/failed_run") {
        val taskId = call.receive<TaskId>()
        MongoExpressionNetwork.failedRun(taskId)
    }
}