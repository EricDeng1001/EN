package cli

import infra.db.mongo.MongoNodeRepository
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) {
    runBlocking {
        val queryAllRoot = MongoNodeRepository.queryAllRoot()
        queryAllRoot.forEach { it.shouldUpdate = true }
        MongoNodeRepository.saveAll(queryAllRoot)
    }
}