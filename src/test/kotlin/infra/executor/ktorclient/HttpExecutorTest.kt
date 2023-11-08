package infra.executor.ktorclient

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import model.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach

class HttpExecutorTest {

    private val server = embeddedServer(Netty, port = 7788) {
        module()
    }

    private val runRequestBody = RunRequestBody(
        Expression(
            inputs = listOf(DataId("open"), DataId("close")),
            outputs = listOf(DataId("output")),
            funcId = FuncId("func1"),
            shapeRule = Expression.ShapeRule(1, 1),
            alignmentRule = Expression.AlignmentRule(mapOf(Pair(DataId("open"), 0), Pair(DataId("close"), 0))),
            arguments = mapOf(Pair("arg1", Argument(type = "float", value = "10")))
        ), Pointer.ZERO, Pointer(10), "xxx"
    )
    private val runResponseBody = RunResponseBody("xxx", true)

    private val tryCancelResponseBody = TryCancelResponseBody("xxx", true)

    private fun Application.module() {
        install(ContentNegotiation) {
            json(json = Json {
                ignoreUnknownKeys = false
                encodeDefaults = true
            })
        }

        routing {
            post("/run") {
                val req = call.receive<RunRequestBody>()
                assertEquals(req, runRequestBody)
                call.respond(runResponseBody)
            }

            put("/cancel/{id}") {
                call.respond(tryCancelResponseBody)
            }
        }
    }

    @BeforeEach
    fun setUp() {
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.stop(1000, 2000)
    }

    @Test
    fun run() {
        HttpExecutor.run(runRequestBody.expression, runRequestBody.from, runRequestBody.to, runRequestBody.withId)
    }

    @Test
    fun tryCancel() {
        HttpExecutor.tryCancel(tryCancelResponseBody.id)
    }

}