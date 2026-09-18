package io.github.ngirchev.fsm.example

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class FsmExampleApplication

fun main(args: Array<String>) {
    runApplication<FsmExampleApplication>(*args)
}
