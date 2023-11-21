package web

import infra.db.mongo.ExpressionNetworkImpl
import infra.messagequeue.WebSocketNotification
import infra.messagequeue.registerConnection
import infra.messagequeue.unregisterConnection
import io.ktor.http.*
import io.ktor.serialization.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.serialization.Serializable
import model.*
import org.slf4j.LoggerFactory

@Serializable
data class RunRootRequest(
    val dataId: String, val effectivePtr: Int
)

private val logger = LoggerFactory.getLogger("Routes")

@Serializable
data class RunResponse(val taskId: String)

fun Route.httpRoutes() {

    get("/graph") {
        try {
            val ids: List<DataId> =
                call.request.queryParameters.getAll("id")?.map { DataId(it) }?.toList() ?: return@get call.respond(
                    HttpStatusCode.BadRequest
                )
            val graph = ExpressionNetworkImpl.buildGraph(ids)
            call.respond(graph.view())
        } catch (e: ContentTransformationException) {
            call.respond(HttpStatusCode.BadRequest)
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, "$e")
        }
    }

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
        val res = call.receive<RunResponse>()
        ExpressionNetworkImpl.succeedRun(res.taskId)
        call.respond(res)
    }

    post("/failed_run") {
        val res = call.receive<RunResponse>()
        ExpressionNetworkImpl.failedRun(res.taskId)
        call.respond(res)
    }
}

fun Route.websocketRoutes() {
    webSocket("/ws") {
        val thisConnection = this
        try {
            for (frame in incoming) {
                frame as? Frame.Text ?: continue
                try {
                    converter?.deserialize<Set<DataId>>(frame)?.let {
                        WebSocketNotification.registerConnection(thisConnection, it)
                    }
                } catch (e: Exception) {
                    logger.info("Exception while processing WebSocket message: ${e.localizedMessage}")
                }
            }
        } catch (e: ClosedSendChannelException) {
            logger.info("Connection closed: $thisConnection")
        } finally {
            // 在连接关闭时从连接池中移除连接
            WebSocketNotification.unregisterConnection(thisConnection)
        }
    }
}