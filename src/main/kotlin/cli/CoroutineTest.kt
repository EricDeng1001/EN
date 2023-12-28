package cli

import io.ktor.server.application.*
import io.ktor.server.response.*
import kotlinx.coroutines.*
import web.fib

val dispatcher = newFixedThreadPoolContext(2, "dispatcher")
val scope = CoroutineScope(dispatcher)

suspend fun main() {
    val job = scope.launch {
        launch {
            val y = fib(44)
            println("fib(44):$y")
        }
        launch {
            val x = fib(45)
            println("fib(45):$x")
        }
        println("job launched 2")
    }
    println("job launched 1")

    job.join()
}