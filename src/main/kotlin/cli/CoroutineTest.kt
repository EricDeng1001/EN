package cli

import io.ktor.server.application.*
import io.ktor.server.response.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import web.fib

fun main() {
    runBlocking {
        launch {
            val x = fib(40)
            println(x)
            delay(10000)
        }
        println("1234")
    }
}