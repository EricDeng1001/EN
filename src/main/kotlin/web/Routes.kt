package web

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import model.DataId
import model.Expression
import model.ExpressionNetwork
import model.FuncId

fun Route.routes(expressionNetwork: ExpressionNetwork) {
    post("/run") {
        val dataId = call.receive<DataId>()
        expressionNetwork.run(dataId)
        call.respond(HttpStatusCode.OK)
    }

    post("/add") {
        val expression = call.receive<Expression>()
        val ids = expressionNetwork.add(expression)
        call.respond(ids)
    }

    post("/update_func") {
        val funcId = call.receive<FuncId>()
        expressionNetwork.updateFunc(funcId)
        call.respond(HttpStatusCode.OK)
    }
}