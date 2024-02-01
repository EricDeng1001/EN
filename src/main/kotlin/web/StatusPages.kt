package web

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.logging.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import kotlinx.serialization.Serializable
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val logger: Logger = LoggerFactory.getLogger("Main")


// 报错统一返回响应体
@Serializable
data class ErrorResponse(val message: String?)

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            when (cause) {
                is ContentTransformationException,
                is IllegalArgumentException
                -> {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.message))
                }

                else -> {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse(cause.message))

                }
            }
            logger.error("${call.request.toLogString()}, ${cause.printStackTrace()}")
        }
    }
}