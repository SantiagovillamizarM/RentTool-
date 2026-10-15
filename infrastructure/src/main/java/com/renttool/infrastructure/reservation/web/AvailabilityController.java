package com.renttool.infrastructure.reservation.web;

import com.renttool.application.reservation.port.in.CheckAvailabilityUseCase;
import com.renttool.domain.reservation.RentalPeriod;
import com.renttool.infrastructure.reservation.web.ReservationDtos.AvailabilityResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tools/{toolId}/availability")
@Tag(name = "Reservas")
public class AvailabilityController {
  private final CheckAvailabilityUseCase availability;

  public AvailabilityController(CheckAvailabilityUseCase availability) {
    this.availability = availability;
  }

  @GetMapping
  @Operation(
      summary = "Consultar disponibilidad por fechas",
      description =
          "Público. Cuenta las unidades OPERATIVAS que no están asignadas a una reserva aceptada"
              + " en un rango que se cruce. Es informativa: no aparta unidades.")
  @ApiResponse(responseCode = "200", description = "Unidades libres y total estimado")
  @ApiResponse(
      responseCode = "400",
      description = "Fechas inválidas, pasadas o cantidad fuera de rango")
  @ApiResponse(responseCode = "404", description = "La herramienta no existe o fue retirada")
  public AvailabilityResponse check(
      @PathVariable Long toolId,
      @Parameter(description = "Día de entrega", example = "2026-10-20")
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate start,
      @Parameter(description = "Día de devolución (no se cobra)", example = "2026-10-23")
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate end,
      @Parameter(description = "Unidades que se quieren alquilar", example = "1")
          @RequestParam(defaultValue = "1")
          int quantity) {
    return AvailabilityResponse.from(
        availability.check(toolId, new RentalPeriod(start, end), quantity));
  }
}
