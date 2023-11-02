import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import web.routes

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

