package web

import infra.db.mongo.ExpressionNetworkImpl
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import model.*

@Serializable
data class RunRootRequest(
    val dataId: String,
    val effectivePtr: Int
)

fun Route.routes() {
    post("/runRoot") {
        val req = call.receive<RunRootRequest>()
        ExpressionNetworkImpl.runRoot(DataId(req.dataId), Pointer(req.effectivePtr))
        call.respond(HttpStatusCode.OK)
    }

    post("runExpression") {
        val req = call.receive<DataId>()
        ExpressionNetworkImpl.runExpression(req)
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