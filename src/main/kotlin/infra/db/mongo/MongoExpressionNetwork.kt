package infra.db.mongo

import infra.datamanager.ktorclient.HttpDataManager
import infra.executor.ktorclient.HttpExecutor
import model.*

object MongoExpressionNetwork : ExpressionNetwork(
    MongoNodeRepository,
    MongoTaskRepository,
    HttpDataManager ,
    HttpExecutor){

}