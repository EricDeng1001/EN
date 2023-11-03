package infra.db.mongo

import model.ExpressionNetwork

object MongoExpressionNetwork : ExpressionNetwork(MongoNodeRepository) {

}