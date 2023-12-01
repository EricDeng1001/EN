package web

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import kotlinx.serialization.Serializable

// 报错统一返回响应体
@Serializable
data class ErrorResponse(val message: String?)

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            if (cause is ContentTransformationException) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.message))
            } else {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(cause.message))
            }
        }
    }
}