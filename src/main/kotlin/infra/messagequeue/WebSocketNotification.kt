package infra.messagequeue

import io.ktor.server.websocket.*
import kotlinx.serialization.Serializable
import model.DataId
import model.MessageQueue
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class SendMessage(val id: DataId, val status: String);

object WebSocketNotification : MessageQueue {

    private val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    val connections = ConcurrentHashMap<WebSocketServerSession, Set<DataId>>()
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
}

fun WebSocketNotification.registerConnection(session: WebSocketServerSession, dataIds: Set<DataId>) {
    connections[session] = dataIds
}

fun WebSocketNotification.unregisterConnection(session: WebSocketServerSession) {
    connections.remove(session)
}