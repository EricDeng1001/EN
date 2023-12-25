package web

import infra.ExpressionNetworkImpl
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
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import model.*
import org.slf4j.LoggerFactory
import web.plugin.adminCheck

@Serializable
data class RunRootRequest(
    val dataId: String, val effectivePtr: Int
)

private val logger = LoggerFactory.getLogger("Routes")

@Serializable
data class RunSuccessResponse(val taskId: String)

@Serializable
data class RunErrorResponse(val taskId: String, val message: String, val type: String)

@Serializable
data class WebSocketRequest(val sub: Set<DataId>? = emptySet(), val unsub: Set<DataId>? = emptySet())

@Serializable
data class ExpressionState(val id: DataId, val state: String? = null)

fun Route.httpRoutes() {

    get("/graph") {
        val ids: List<DataId> = call.request.queryParameters.getAll("id")?.map { DataId(it) }?.toList()
            ?: throw IllegalArgumentException("id is required")
        call.respond(ExpressionNetworkImpl.buildGraph(ids))
    }

    get("/graph/debug") {
        val ids: List<DataId> = call.request.queryParameters.getAll("id")?.map { DataId(it) }?.toList()
            ?: throw IllegalArgumentException("id is required")
        call.respond(ExpressionNetworkImpl.buildDebugGraph(ids))
    }

    get("/expression/state") {
        val ids: List<DataId> = call.request.queryParameters.getAll("id")?.map { DataId(it) }?.toList()
            ?: throw IllegalArgumentException("id is required")
        call.respond(ExpressionNetworkImpl.queryExpressionsState(ids).map { ExpressionState(it.first, it.second) })
    }

    post("/run/root") {
        val req = call.receive<RunRootRequest>()
        ExpressionNetworkImpl.updateRoot(DataId(req.dataId), Pointer(req.effectivePtr))
        call.respond(HttpStatusCode.OK)
    }

    post("run/expression") {
        val force = call.request.queryParameters["force"] ?: ""
        val req = call.receive<DataId>()
        if (force != "") {
            ExpressionNetworkImpl.markForceRunPerf(req)
        }
        ExpressionNetworkImpl.runExpression(req)
        call.respond(HttpStatusCode.OK)
    }

    post("/add") {
        val expression = call.receive<Expression>()
        val ids = ExpressionNetworkImpl.add(expression)
        call.respond(ids)
    }

    post("/add/root") {
        val id = call.receive<DataId>()
        ExpressionNetworkImpl.add(Expression.makeRoot(id))
        call.respond(HttpStatusCode.OK)
    }

    post("/update_func") {
        val funcId = call.receive<FuncId>()
        launch {
            ExpressionNetworkImpl.updateFunc(funcId)
        }
        call.respond(HttpStatusCode.OK)
    }

    post("/succeed_run") {
        val res = call.receive<RunSuccessResponse>()
        launch {
            ExpressionNetworkImpl.succeedRun(res.taskId)
        }
        call.respond(res)
    }

    post("/failed_run") {
        val res = call.receive<RunErrorResponse>()
        launch {
            if (res.type == NodeState.FAILED.value) {
                ExpressionNetworkImpl.failedRun(res.taskId, res.message)
            } else if (res.type == NodeState.SYSTEM_FAILED.value) {
                ExpressionNetworkImpl.systemFailedRun(res.taskId, res.message)
            }
        }
        call.respond(res)
    }

    post("/mark_must") {
        val ids = call.receive<List<DataId>>()
        ExpressionNetworkImpl.markMustCalc(ids)
        call.respond(HttpStatusCode.OK)
    }

    post("/mark_should_update") {
        val ids = call.receive<List<DataId>>()
        ExpressionNetworkImpl.markShouldUpdate(ids)
        call.respond(HttpStatusCode.OK)
    }
}

fun Route.adminHttpRoutes(){
    adminCheck {
        get("/tasks") {
            val ids: List<DataId> = call.request.queryParameters.getAll("id")?.map { DataId(it) }?.toList()
                ?: emptyList()
            if (ids.isEmpty()){
                call.respond(HttpStatusCode.OK, emptyList<Task>())
                return@get
            }
            call.respond(HttpStatusCode.OK, ExpressionNetworkImpl.getTasksByDataId(ids))
        }
    }
}

fun Route.websocketRoutes() {
    webSocket("/listen") {
        val thisConnection = this
        try {
            for (frame in incoming) {
                frame as? Frame.Text ?: continue
                try {
                    converter?.deserialize<WebSocketRequest>(frame)?.let {
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