package com.saibonthala.inventory.api;

import com.saibonthala.inventory.domain.InsufficientStockException;
import com.saibonthala.inventory.service.ConflictException;
import com.saibonthala.inventory.service.InvalidMovementException;
import com.saibonthala.inventory.service.NotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps exceptions to RFC 7807 problem responses. Extending ResponseEntityExceptionHandler
 * also turns bean-validation failures into consistent 400 responses.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Resource not found", e.getMessage());
    }

    @ExceptionHandler(InvalidMovementException.class)
    public ProblemDetail handleInvalid(InvalidMovementException e) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid movement", e.getMessage());
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ProblemDetail handleInsufficientStock(InsufficientStockException e) {
        ProblemDetail detail = problem(HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient stock", e.getMessage());
        detail.setProperty("sku", e.getSku());
        detail.setProperty("locationCode", e.getLocationCode());
        detail.setProperty("available", e.getAvailable());
        detail.setProperty("requested", e.getRequested());
        return detail;
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException e) {
        return problem(HttpStatus.CONFLICT, "Conflict", e.getMessage());
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, DataIntegrityViolationException.class})
    public ProblemDetail handleConcurrentUpdate(RuntimeException e) {
        return problem(HttpStatus.CONFLICT, "Concurrent update",
                "The stock record was changed by another request. Retry with the same idempotency key.");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
