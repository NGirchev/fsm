package io.github.ngirchev.fsm.spring.admin

import io.github.ngirchev.fsm.exception.FsmException
import org.springframework.http.HttpStatus
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice(assignableTypes = [FsmAdminController::class])
class FsmAdminExceptionHandler {
    @ExceptionHandler(NoSuchElementException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun missing(error: NoSuchElementException) = mapOf("message" to error.message)

    @ExceptionHandler(IllegalStateException::class, FsmException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun conflict(error: RuntimeException) = mapOf("message" to error.message)

    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun invalid(error: IllegalArgumentException) = mapOf("message" to error.message)

    @ExceptionHandler(HttpMessageNotReadableException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun malformed() = mapOf("message" to "Invalid flow definition JSON")
}
