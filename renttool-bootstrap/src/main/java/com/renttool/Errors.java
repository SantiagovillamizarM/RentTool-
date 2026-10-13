package com.renttool;

import com.renttool.shared.BusinessException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class Errors {
  @ExceptionHandler(BusinessException.class)
  ResponseEntity<ProblemDetail> business(BusinessException e) {
    return problem(e.status(), e.getMessage());
  }

  @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
  ResponseEntity<ProblemDetail> validation(
      org.springframework.web.bind.MethodArgumentNotValidException e) {
    return problem(
        400,
        e.getBindingResult().getFieldErrors().stream()
            .map(x -> x.getField() + ": " + x.getDefaultMessage())
            .collect(java.util.stream.Collectors.joining("; ")));
  }

  @ExceptionHandler({
    org.springframework.http.converter.HttpMessageNotReadableException.class,
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
    IllegalArgumentException.class
  })
  ResponseEntity<ProblemDetail> malformed(Exception e) {
    return problem(400, "Datos inválidos. Revisa los campos enviados.");
  }

  @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
  ResponseEntity<ProblemDetail> conflict(Exception e) {
    return problem(409, "El registro está duplicado o tiene relaciones inválidas");
  }

  @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
  ResponseEntity<ProblemDetail> uploadTooLarge(Exception e) {
    return problem(400, "La imagen supera el límite de 5 MB");
  }

  private ResponseEntity<ProblemDetail> problem(int status, String detail) {
    return ResponseEntity.status(status)
        .body(ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), detail));
  }
}
