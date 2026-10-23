
package com.garmentfactory.inventory.exception;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps inventory rule violations to the same problem+json shape as GlobalExceptionHandler.
 */
@RestControllerAdvice(basePackages = "com.garmentfactory.inventory.controller")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InventoryExceptionHandler {

    @ExceptionHandler(InsufficientStockException.class)
    ResponseEntity<ProblemDetail> insufficient(InsufficientStockException ex) {
        return problem(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", ex.getMessage());
    }

    @ExceptionHandler(StockConflictException.class)
    ResponseEntity<ProblemDetail> conflict(StockConflictException ex) {
        return problem(HttpStatus.CONFLICT, "STOCK_CONFLICT", ex.getMessage());
    }

    @ExceptionHandler(StockNotFoundException.class)
    ResponseEntity<ProblemDetail> notFound(StockNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "STOCK_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(InvalidStockOperationException.class)
    ResponseEntity<ProblemDetail> invalid(InvalidStockOperationException ex) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_STOCK_OPERATION", ex.getMessage());
    }

    private static ResponseEntity<ProblemDetail> problem(
            HttpStatus status, String code, String detail) {

        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setTitle(status.getReasonPhrase());
        body.setProperty("code", code);

        return ResponseEntity.status(status).body(body);
    }
}
