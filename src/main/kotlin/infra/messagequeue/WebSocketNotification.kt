package infra.messagequeue

import io.ktor.server.websocket.*
import kotlinx.serialization.Serializable
import model.DataId
import model.MessageQueue
import model.NodeState
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import web.WebSocketRequest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

@Serializable
data class SendMessage(val id: DataId, val status: String, val reason: String = "");

object WebSocketNotification : MessageQueue {

    private val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    val connections = ConcurrentHashMap<WebSocketServerSession, CopyOnWriteArraySet<DataId>>()
    override suspend fun pushRunning(id: DataId) {
        connections.forEach { (session, dataIds) ->
            if (dataIds.contains(id)) {
                try {
                    session.sendSerialized(SendMessage(id, NodeState.RUNNING.value))
                } catch (e: Exception) {
                    logger.error("Error sending running message to client: $e")
                }
            }
        }
    }

    override suspend fun pushRunFailed(id: DataId, reason: String) {
        connections.forEach { (session, dataIds) ->
            if (dataIds.contains(id)) {
                try {
                    session.sendSerialized(SendMessage(id, NodeState.FAILED.value, reason))
                } catch (e: Exception) {
                    logger.error("Error sending failed message to client: $e")
                }
            }
        }
    }

    override suspend fun pushRunFinish(id: DataId) {
        connections.forEach { (session, dataIds) ->
            if (dataIds.contains(id)) {
                try {
                    session.sendSerialized(SendMessage(id, NodeState.FINISHED.value))
                } catch (e: Exception) {
                    logger.error("Error sending finish message to client: $e")
                }
            }
        }
    }

    override suspend fun pushSystemFailed(id: DataId) {
        connections.forEach { (session, dataIds) ->
            if (dataIds.contains(id)) {
                try {
                    session.sendSerialized(SendMessage(id, NodeState.SYSTEM_FAILED.value))
                } catch (e: Exception) {
                    logger.error("Error sending system-failed message to client: $e")
                }
            }
        }
    }
}

fun WebSocketNotification.registerConnection(session: WebSocketServerSession, request: WebSocketRequest) {
    val subs = connections.computeIfAbsent(session) { CopyOnWriteArraySet() }
    subs.addAll(request.sub ?: emptySet())
    subs.removeAll((request.unsub ?: emptySet()).toSet())
}

fun WebSocketNotification.unregisterConnection(session: WebSocketServerSession) {
    connections.remove(session)
}