package cli

import infra.db.mongo.MongoNodeRepository
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) {
    runBlocking {
        val queryAllRoot = MongoNodeRepository.queryAllRoot()
        for (root in queryAllRoot) {
            root.shouldUpdate = true
        }
        MongoNodeRepository.saveAll(queryAllRoot)
    }
}