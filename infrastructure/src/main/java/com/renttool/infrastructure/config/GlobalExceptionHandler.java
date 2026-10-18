package com.renttool.infrastructure.config;

import com.renttool.domain.shared.exception.*;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.validation.FieldError;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
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
    var errors = new LinkedHashMap<String, String>();
    e.getBindingResult().getFieldErrors().stream()
        .sorted(java.util.Comparator.comparing(FieldError::getField)
            .thenComparingInt(x -> "NotBlank".equals(x.getCode()) || "NotNull".equals(x.getCode()) ? 0 : 1))
        .forEach(x -> errors.putIfAbsent(x.getField(), validationMessage(x)));
    var body = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
        String.join("; ", errors.values()));
    body.setProperty("fieldErrors", errors);
    return ResponseEntity.badRequest().body(body);
  }

  private static final Map<String, String> FIELD_NAMES = Map.ofEntries(
      Map.entry("name", "Nombre"), Map.entry("email", "Correo electrónico"),
      Map.entry("password", "Contraseña"), Map.entry("temporaryPassword", "Contraseña temporal"),
      Map.entry("currentPassword", "Contraseña actual"), Map.entry("newPassword", "Nueva contraseña"),
      Map.entry("categoryId", "Categoría"), Map.entry("description", "Descripción"),
      Map.entry("dailyRate", "Tarifa diaria"), Map.entry("code", "Código de unidad"),
      Map.entry("status", "Estado"), Map.entry("location", "Dirección de imagen"),
      Map.entry("start", "Fecha de entrega"), Map.entry("end", "Fecha de devolución"),
      Map.entry("quantity", "Cantidad"), Map.entry("count", "Unidades"), Map.entry("toolId", "Herramienta"),
      Map.entry("role", "Rol"), Map.entry("reason", "Motivo"),
      Map.entry("personType", "Tipo de persona"), Map.entry("nit", "NIT"),
      Map.entry("verificationDigit", "Dígito de verificación"), Map.entry("legalName", "Razón social o nombre completo"),
      Map.entry("tradeName", "Nombre comercial"), Map.entry("legalRepresentative", "Representante legal"),
      Map.entry("representativeDocument", "Documento del representante"), Map.entry("phone", "Teléfono"),
      Map.entry("address", "Dirección"), Map.entry("city", "Ciudad"), Map.entry("profile", "Datos del proveedor"));

  private static String validationMessage(FieldError error) {
    String field = error.getField().substring(error.getField().lastIndexOf('.') + 1);
    String label = FIELD_NAMES.getOrDefault(field, "Campo");
    Object[] args = error.getArguments();
    String message = switch (error.getCode() == null ? "" : error.getCode()) {
      case "NotBlank", "NotNull" -> "es obligatorio.";
      case "Email" -> "escribe una dirección válida, por ejemplo nombre@ejemplo.com.";
      case "Size" -> ((Number) args[2]).intValue() == 0
          ? "admite hasta " + args[1] + " caracteres."
          : "debe tener entre " + args[2] + " y " + args[1] + " caracteres.";
      case "Min", "DecimalMin" -> "debe ser como mínimo " + args[args.length - 1] + ".";
      case "Max", "DecimalMax" -> "debe ser como máximo " + args[args.length - 1] + ".";
      case "Digits" -> "admite hasta " + args[2] + " dígitos enteros y " + args[1] + " decimales.";
      case "Pattern" -> error.getDefaultMessage();
      default -> "revisa el valor introducido.";
    };
    return label + ": " + message;
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ResponseEntity<ProblemDetail> invalidParameter(MethodArgumentTypeMismatchException e) {
    String hint = e.getRequiredType() != null && e.getRequiredType().isEnum()
        ? " Usa uno de estos valores: " + java.util.Arrays.toString(e.getRequiredType().getEnumConstants()) + "."
        : " Revisa el formato del valor enviado.";
    return problem(HttpStatus.BAD_REQUEST, "El parámetro «" + e.getName() + "» no es válido." + hint);
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    IllegalArgumentException.class
  })
  ResponseEntity<ProblemDetail> malformed(Exception e) {
    return problem(HttpStatus.BAD_REQUEST, "Datos inválidos. Revisa los campos enviados.");
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ProblemDetail> conflict(Exception e) {
    return problem(HttpStatus.CONFLICT, "El registro está duplicado o tiene relaciones inválidas");
  }

  /** Espera de bloqueo agotada o interbloqueo detectado por la base de datos. */
  @ExceptionHandler(PessimisticLockingFailureException.class)
  ResponseEntity<ProblemDetail> lockConflict(Exception e) {
    return problem(
        HttpStatus.CONFLICT, "La operación coincidió con otra en curso. Inténtalo de nuevo.");
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
