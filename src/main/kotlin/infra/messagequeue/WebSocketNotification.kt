package infra.messagequeue

import io.ktor.server.websocket.*
import kotlinx.serialization.Serializable
import model.DataId
import model.MessageQueue
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import web.WebSocketRequest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

@Serializable
data class SendMessage(val id: DataId, val status: String);

object WebSocketNotification : MessageQueue {

    private val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    val connections = ConcurrentHashMap<WebSocketServerSession, CopyOnWriteArraySet<DataId>>()
    override suspend fun pushRunning(id: DataId) {
        connections.forEach { (session, dataIds) ->
            if (dataIds.contains(id)) {
                try {
                    session.sendSerialized(SendMessage(id, "running"))
                } catch (e: Exception) {
                    logger.error("Error sending running message to client: $e")
                }
            }
        }
    }

    override suspend fun pushRunFailed(id: DataId) {
        connections.forEach { (session, dataIds) ->
            if (dataIds.contains(id)) {
                try {
                    session.sendSerialized(SendMessage(id, "failed"))
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
                    session.sendSerialized(SendMessage(id, "finish"))
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
                    session.sendSerialized(SendMessage(id, "system-failed"))
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