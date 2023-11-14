package web

import infra.db.mongo.ExpressionNetworkImpl
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
        ExpressionNetworkImpl.run(dataId)
        call.respond(HttpStatusCode.OK)
    }

    post("/add") {
        val expression = call.receive<Expression>()
        val ids = ExpressionNetworkImpl.add(expression)
        call.respond(ids)
    }

    post("/addRoot") {
        val id = call.receive<DataId>()
        ExpressionNetworkImpl.add(Expression.makeRoot(id))
        call.respond(HttpStatusCode.OK)
    }

    post("/update_func") {
        val funcId = call.receive<FuncId>()
        ExpressionNetworkImpl.updateFunc(funcId)
        call.respond(HttpStatusCode.OK)
    }

    post("/succeed_run") {
        val taskId = call.receive<TaskId>()
        ExpressionNetworkImpl.succeedRun(taskId)
    }

    post("/failed_run") {
        val taskId = call.receive<TaskId>()
        ExpressionNetworkImpl.failedRun(taskId)
    }
}