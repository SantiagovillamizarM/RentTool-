package com.renttool.infrastructure.review.web;

import static com.renttool.infrastructure.config.OpenApiConfig.BEARER;

import com.renttool.application.review.port.in.ReviewToolsUseCase;
import com.renttool.application.review.port.in.ReviewToolsUseCase.*;
import com.renttool.domain.reservation.Participant;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Calificaciones", description = "El cliente califica la herramienta tras devolverla")
public class ReviewController {
  private final ReviewToolsUseCase reviews;

  public ReviewController(ReviewToolsUseCase reviews) {
    this.reviews = reviews;
  }

  public record RateRequest(
      @NotNull @Min(1) @Max(5) Integer rating, @Size(max = 500) String comment) {}

  @PostMapping("/reservations/{id}/review")
  @ResponseStatus(HttpStatus.CREATED)
  @SecurityRequirement(name = BEARER)
  @Operation(
      summary = "Calificar una reserva",
      description = "Solo el CLIENTE de la reserva, una vez y después de registrada la devolución.")
  @ApiResponse(responseCode = "201", description = "Calificación guardada")
  @ApiResponse(responseCode = "403", description = "No es el cliente de la reserva")
  @ApiResponse(responseCode = "409", description = "Aún no se devuelve o ya fue calificada")
  public ReviewView rate(
      @PathVariable Long id,
      @Valid @RequestBody RateRequest r,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return reviews.rate(actor(jwt), id, new RateCommand(r.rating(), r.comment()));
  }

  @GetMapping("/reservations/{id}/review")
  @SecurityRequirement(name = BEARER)
  @Operation(summary = "Ver la calificación de una reserva y si aún se puede calificar")
  public ReservationReview ofReservation(
      @PathVariable Long id, @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return reviews.ofReservation(actor(jwt), id);
  }

  @GetMapping("/tools/{id}/reviews")
  @Operation(summary = "Calificaciones de una herramienta", description = "Público.")
  @ApiResponse(responseCode = "404", description = "La herramienta no existe")
  public ToolReviews ofTool(@PathVariable Long id) {
    return reviews.ofTool(id);
  }

  private static Participant actor(Jwt jwt) {
    return new Participant(
        Long.valueOf(jwt.getSubject()), Participant.Role.valueOf(jwt.getClaimAsString("role")));
  }
}
