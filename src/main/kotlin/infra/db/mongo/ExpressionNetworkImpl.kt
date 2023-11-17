package infra.db.mongo

import infra.executor.ktorclient.HttpExecutor
import infra.messagequeue.WebSocketNotification
import model.*

object ExpressionNetworkImpl : ExpressionNetwork(
    MongoNodeRepository,
    MongoTaskRepository,
    HttpExecutor,
    WebSocketNotification
) {

}