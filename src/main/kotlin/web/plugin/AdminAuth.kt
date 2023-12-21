package web.plugin

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import infra.HttpHorizonService
import io.ktor.server.routing.*
import io.ktor.util.*
import model.User
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("AccessInfo")

val USER_KEY = AttributeKey<User>("me")


fun Route.adminCheck(callback: Route.() -> Unit): Route {
    // With createChild, we create a child node for this received Route
    val adminRouteSelector = this.createChild(object : RouteSelector() {
        override fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation =
            RouteSelectorEvaluation.Constant
    })

    // Intercepts calls from this route at the features step
    adminRouteSelector.intercept(ApplicationCallPipeline.Call) {
        val user: User?
        if (!call.attributes.contains(USER_KEY)) {
            // query token
            val token = call.request.headers["Authorization"]
            if (token.isNullOrBlank()) {
                call.respond(HttpStatusCode.Unauthorized)
                return@intercept
            }
            user = HttpHorizonService.queryUserInfoByToken(token)
            if (user == null) {
                call.respond(HttpStatusCode.Unauthorized)
                return@intercept
            }
            call.attributes.put(USER_KEY, user)
        } else {
            user = call.attributes[USER_KEY]
        }

        if (!user.isAdmin()) {
            call.respond(HttpStatusCode.Forbidden,"You are not admin member, can't access this page")
            return@intercept
        }

        logger.info("Path: ${call.request.path()}, HTTPMethod: ${call.request.httpMethod}, User: ${user.email}")
    }

    // Configure this route with the block provided by the user
    adminRouteSelector.callback()

    return adminRouteSelector
}
