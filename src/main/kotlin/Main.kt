import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlinx.serialization.json.Json
import web.httpRoutes
import web.websocketRoutes
import java.time.Duration

fun main(args: Array<String>) = EngineMain.main(args)

fun Application.module() {
    configureSockets()
    configureRouting()
    configureSerialization()
}

fun Application.configureSockets() {
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
        contentConverter = KotlinxWebsocketSerializationConverter(Json {
            ignoreUnknownKeys = false
            encodeDefaults = true
        })
    }
}

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(json = Json {
            ignoreUnknownKeys = false
            encodeDefaults = true
        })
    }
}

fun Application.configureRouting() {
    routing {
        httpRoutes()
        websocketRoutes()
    }
}