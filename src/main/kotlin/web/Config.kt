package web

import com.mongodb.reactivestreams.client.MongoClients
import com.mongodb.reactivestreams.client.MongoDatabase
import infra.db.mongo.NodeMongoRepository
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import model.ExpressionNetwork

fun Application.module() {
    val database = configureMongoDatabase()
    val expressNetwork = ExpressionNetwork.getInstance(NodeMongoRepository(database))

    configureRouting(expressNetwork)
    configureSerialization()
}

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(json = Json {
            ignoreUnknownKeys = false
            encodeDefaults = true
        })
    }
}

fun Application.configureRouting(expressionNetwork: ExpressionNetwork) {
    routing {
        routes(expressionNetwork)
    }
}

fun Application.configureMongoDatabase(): MongoDatabase {
    val url = environment.config.property(Constants.MONGODB_URL).getString()
    val db = environment.config.property(Constants.MONGODB_DATABASE_NAME).getString()
    val client = MongoClients.create(url)
    return client.getDatabase(db)
}