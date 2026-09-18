package io.github.ngirchev.fsm.example

import io.github.ngirchev.fsm.example.flow.FlowConflictException
import io.github.ngirchev.fsm.example.flow.FlowValidationIssue
import io.github.ngirchev.fsm.example.flow.InvalidFlowDefinitionException
import io.github.ngirchev.fsm.exception.FsmException
import org.springframework.http.HttpStatus
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

data class ApiError(val message: String, val issues: List<FlowValidationIssue> = emptyList())

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(NoSuchElementException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun notFound(error: NoSuchElementException) = ApiError(error.message ?: "Not found")

    @ExceptionHandler(FlowConflictException::class, FsmException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun conflict(error: RuntimeException) = ApiError(error.message ?: "Conflict")

    @ExceptionHandler(InvalidFlowDefinitionException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun invalidFlow(error: InvalidFlowDefinitionException) = ApiError(error.message ?: "Invalid flow", error.issues)

    @ExceptionHandler(IllegalArgumentException::class, HttpMessageNotReadableException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun badRequest(error: Exception) = ApiError(error.message ?: "Bad request")
}
