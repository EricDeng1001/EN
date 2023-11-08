package infra.datamanager.ktorclient

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import model.DataId
import model.Pointer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*

class HttpDataManagerTest {
    private val server = embeddedServer(Netty, port = 7788) {
        module()
    }

    private fun Application.module() {
        install(ContentNegotiation) {
            json(json = Json {
                ignoreUnknownKeys = false
                encodeDefaults = true
            })
        }

        routing {
            get("/find-last-ptr/{id}") {
                call.respondText("10", ContentType.Application.Json, HttpStatusCode.OK)
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
    fun findLastPtr() {
        val pointer = HttpDataManager.findLastPtr(DataId("xxx"))
        assertEquals(Pointer(10), pointer)
    }

}

