package com.renttool.infrastructure.reservation.web;

import static com.renttool.infrastructure.config.OpenApiConfig.BEARER;

import com.renttool.application.reservation.port.in.ManageReservationsUseCase;
import com.renttool.application.shared.PageQuery;
import com.renttool.infrastructure.inventory.web.InventoryDtos.PageResponse;
import com.renttool.infrastructure.reservation.web.ReservationDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reservations")
@Tag(
    name = "Reservas",
    description =
        "Solicitud por fechas, aceptación con asignación de unidades, rechazo y cancelación")
@SecurityRequirement(name = BEARER)
@ApiResponse(responseCode = "401", description = "Sin token, token vencido o cuenta inactiva")
public class ReservationController {
  private final ManageReservationsUseCase reservations;
  private final com.renttool.application.operations.port.in.ManageRentalsUseCase operations;

  public ReservationController(ManageReservationsUseCase reservations,
      com.renttool.application.operations.port.in.ManageRentalsUseCase operations) {
    this.operations = operations;
    this.reservations = reservations;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Solicitar una reserva",
      description =
          "Solo CLIENTE. Valida fechas y disponibilidad y guarda la tarifa vigente. Queda en"
              + " SOLICITADA hasta que el proveedor responda o venza el plazo.")
  @ApiResponse(responseCode = "201", description = "Solicitud creada")
  @ApiResponse(responseCode = "400", description = "Fechas inválidas o pasadas")
  @ApiResponse(responseCode = "403", description = "Quien solicita no es CLIENTE")
  @ApiResponse(responseCode = "404", description = "La herramienta no existe")
  @ApiResponse(responseCode = "409", description = "No hay unidades libres o fue retirada")
  public ReservationResponse request(
      @Valid @RequestBody ReservationRequest r,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return response(ParticipantResolver.from(jwt),
        reservations.request(ParticipantResolver.from(jwt), r.toCommand()));
  }

  @GetMapping
  @Operation(
      summary = "Listar reservas",
      description =
          "CLIENTE ve las suyas, PROVEEDOR las de sus herramientas y ADMINISTRADOR todas.")
  @ApiResponse(responseCode = "200", description = "Página de reservas, la más reciente primero")
  public PageResponse<ReservationResponse> list(
      @Parameter(description = "Página, desde 0") @RequestParam(defaultValue = "0") int page,
      @Parameter(description = "Tamaño de página, máximo " + PageQuery.MAX_SIZE)
          @RequestParam(defaultValue = "20")
          int size,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return PageResponse.from(
        reservations
            .list(ParticipantResolver.from(jwt), new PageQuery(page, size))
            .map(details -> response(ParticipantResolver.from(jwt), details)));
  }

  @GetMapping("/{id}")
  @Operation(summary = "Ver una reserva")
  @ApiResponse(responseCode = "200", description = "Reserva")
  @ApiResponse(responseCode = "403", description = "No es del cliente ni del proveedor")
  @ApiResponse(responseCode = "404", description = "La reserva no existe")
  public ReservationResponse get(
      @PathVariable Long id, @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return response(ParticipantResolver.from(jwt), reservations.get(ParticipantResolver.from(jwt), id));
  }

  @GetMapping("/{id}/history")
  @Operation(
      summary = "Historial de una reserva",
      description =
          "Cada cambio con fecha, acción y quién lo hizo, del más antiguo al más reciente. Los"
              + " cambios anteriores a que existiera el historial llegan con recorded=false.")
  @ApiResponse(responseCode = "200", description = "Línea de tiempo")
  @ApiResponse(responseCode = "403", description = "No es del cliente ni del proveedor")
  @ApiResponse(responseCode = "404", description = "La reserva no existe")
  public List<HistoryResponse> history(
      @PathVariable Long id, @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return reservations.history(ParticipantResolver.from(jwt), id).stream()
        .map(HistoryResponse::from)
        .toList();
  }

  @PostMapping("/{id}/accept")
  @Operation(
      summary = "Aceptar una solicitud",
      description =
          "Solo el PROVEEDOR de la herramienta. Bloquea la herramienta, vuelve a comprobar"
              + " disponibilidad y asigna unidades concretas.")
  @ApiResponse(responseCode = "200", description = "Reserva aceptada con unidades asignadas")
  @ApiResponse(responseCode = "403", description = "No es el proveedor de la herramienta")
  @ApiResponse(responseCode = "404", description = "La reserva no existe")
  @ApiResponse(
      responseCode = "409",
      description = "Ya no está pendiente, la fecha pasó o no quedan unidades libres")
  public ReservationResponse accept(
      @PathVariable Long id, @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return response(ParticipantResolver.from(jwt), reservations.accept(ParticipantResolver.from(jwt), id));
  }

  @PostMapping("/{id}/reject")
  @Operation(
      summary = "Rechazar una solicitud",
      description = "Solo el PROVEEDOR de la herramienta. El motivo es opcional.")
  @ApiResponse(responseCode = "200", description = "Reserva rechazada")
  @ApiResponse(responseCode = "403", description = "No es el proveedor de la herramienta")
  @ApiResponse(responseCode = "404", description = "La reserva no existe")
  @ApiResponse(responseCode = "409", description = "Ya no está pendiente")
  public ReservationResponse reject(
      @PathVariable Long id,
      @Valid @RequestBody(required = false) RejectRequest r,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return response(ParticipantResolver.from(jwt),
        reservations.reject(ParticipantResolver.from(jwt), id, r == null ? null : r.reason()));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Cancelar una reserva",
      description =
          "Cancelación lógica por el CLIENTE que la pidió o un ADMINISTRADOR. Una aceptada solo"
              + " se cancela antes del día de inicio. Libera las unidades.")
  @ApiResponse(responseCode = "204", description = "Reserva cancelada")
  @ApiResponse(responseCode = "403", description = "No es el cliente de la reserva")
  @ApiResponse(responseCode = "404", description = "La reserva no existe")
  @ApiResponse(responseCode = "409", description = "Ya terminó, se rechazó o el alquiler empezó")
  public void cancel(
      @PathVariable Long id, @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    reservations.cancel(ParticipantResolver.from(jwt), id);
  }
  private ReservationResponse response(com.renttool.domain.reservation.Participant actor,
      com.renttool.application.reservation.port.in.ReservationDetails details) {
    String status = operations.get(actor, details.reservation().id()).status();
    return ReservationResponse.from(details, status);
  }
}
