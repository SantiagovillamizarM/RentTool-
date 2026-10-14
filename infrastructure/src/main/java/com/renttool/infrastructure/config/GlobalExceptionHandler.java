package com.renttool.infrastructure.config;

import com.renttool.domain.shared.exception.*;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** Traduce las excepciones del dominio y de Spring a respuestas ProblemDetail. */
@RestControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(DomainException.class)
  ResponseEntity<ProblemDetail> domain(DomainException e) {
    return problem(statusOf(e), e.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException e) {
    return problem(
        HttpStatus.BAD_REQUEST,
        e.getBindingResult().getFieldErrors().stream()
            .map(x -> x.getField() + ": " + x.getDefaultMessage())
            .collect(Collectors.joining("; ")));
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    IllegalArgumentException.class
  })
  ResponseEntity<ProblemDetail> malformed(Exception e) {
    return problem(HttpStatus.BAD_REQUEST, "Datos inválidos. Revisa los campos enviados.");
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ProblemDetail> conflict(Exception e) {
    return problem(HttpStatus.CONFLICT, "El registro está duplicado o tiene relaciones inválidas");
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<ProblemDetail> uploadTooLarge(Exception e) {
    return problem(HttpStatus.BAD_REQUEST, "La imagen supera el límite de 5 MB");
  }

  private static HttpStatus statusOf(DomainException e) {
    return switch (e) {
      case InvalidInputException x -> HttpStatus.BAD_REQUEST;
      case AuthenticationFailedException x -> HttpStatus.UNAUTHORIZED;
      case ForbiddenOperationException x -> HttpStatus.FORBIDDEN;
      case NotFoundException x -> HttpStatus.NOT_FOUND;
      case ConflictException x -> HttpStatus.CONFLICT;
    };
  }

  private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
    return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, detail));
  }
}
