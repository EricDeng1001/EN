package infra

import infra.db.mongo.MongoNodeRepository
import infra.db.mongo.MongoTaskRepository
import infra.executor.ktorclient.HttpExecutor
import infra.messagequeue.WebSocketNotification
import model.*

object ExpressionNetworkImpl : ExpressionNetwork(
    MongoNodeRepository,
    MongoTaskRepository,
    HttpExecutor,
    WebSocketNotification,
    HttpPerformanceService,
    HttpSymbolLibrary,
    HttpDataInfo,
) {

}