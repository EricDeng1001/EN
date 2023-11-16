package infra.db.mongo

import infra.executor.ktorclient.HttpExecutor
import model.*

object ExpressionNetworkImpl : ExpressionNetwork(
    MongoNodeRepository,
    MongoTaskRepository,
    HttpExecutor){

}