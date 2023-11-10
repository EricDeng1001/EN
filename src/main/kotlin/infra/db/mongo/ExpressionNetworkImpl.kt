package infra.db.mongo

import infra.datamanager.ktorclient.HttpDataManager
import infra.executor.ktorclient.HttpExecutor
import model.*

object ExpressionNetworkImpl : ExpressionNetwork(
    MongoNodeRepository,
    MongoTaskRepository,
    HttpDataManager ,
    HttpExecutor){

}