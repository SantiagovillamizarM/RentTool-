package com.renttool.infrastructure.operations.web;

import com.renttool.application.operations.port.in.ManageRentalsUseCase;
import com.renttool.application.shared.*;
import com.renttool.domain.operations.*;
import com.renttool.domain.reservation.Participant;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Entregas, devoluciones e incidencias")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = com.renttool.infrastructure.config.OpenApiConfig.BEARER)
public class RentalOperationsController {
  private final ManageRentalsUseCase operations;
  public RentalOperationsController(ManageRentalsUseCase operations) { this.operations = operations; }

  public record DeliveryRequest(@Size(max = 500) String notes) {}
  public record ReturnItem(@NotNull Long unitId, @NotNull UnitReturn.Condition condition,
      @Size(max = 500) String notes,
      @PositiveOrZero @Digits(integer = 12, fraction = 2) BigDecimal charge) {}
  public record ReturnRequest(@Size(max = 500) String notes,
      @NotEmpty @Size(max = 20) List<@NotNull @Valid ReturnItem> units) {}
  public record IncidentRequest(Long unitId, @NotNull Incident.Type type,
      @NotBlank @Size(max = 1000) String description) {}
  public record ResolveRequest(@NotBlank @Size(max = 1000) String resolution, boolean readyToUse) {}

  @GetMapping("/reservations/{id}/operations")
  @Operation(summary = "Consultar entrega, devolución y estado de las unidades")
  public ManageRentalsUseCase.Summary get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
    return operations.get(actor(jwt), id);
  }
  @PostMapping("/reservations/{id}/delivery")
  @Operation(summary = "Registrar entrega física de una reserva aceptada y pagada")
  public ManageRentalsUseCase.Summary deliver(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
      @Valid @RequestBody DeliveryRequest request) {
    return operations.deliver(actor(jwt), id, request.notes());
  }
  @PostMapping("/reservations/{id}/return")
  @Operation(summary = "Registrar devolución y revisar cada unidad",
      description = "Calcula el recargo si se devuelve después de la fecha pactada. Cada unidad"
          + " dañada o perdida puede llevar un cobro (charge).")
  public ManageRentalsUseCase.Summary returnEquipment(@AuthenticationPrincipal Jwt jwt,
      @PathVariable Long id, @Valid @RequestBody ReturnRequest request) {
    return operations.returnEquipment(actor(jwt), id, new ManageRentalsUseCase.ReturnCommand(request.notes(),
        request.units().stream().map(item ->
            new ManageRentalsUseCase.ReturnItem(item.unitId(), item.condition(), item.notes(),
                item.charge())).toList()));
  }
  @PostMapping("/reservations/{id}/incidents")
  @Operation(summary = "Reportar daño, pérdida u otra incidencia")
  public Incident report(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
      @Valid @RequestBody IncidentRequest request) {
    return operations.report(actor(jwt), id,
        new ManageRentalsUseCase.IncidentCommand(request.unitId(), request.type(), request.description()));
  }
  @PostMapping("/incidents/{id}/resolve")
  @Operation(summary = "Resolver incidencia con observaciones y revisión del equipo")
  public Incident resolve(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
      @Valid @RequestBody ResolveRequest request) {
    return operations.resolve(actor(jwt), id, request.resolution(), request.readyToUse());
  }
  @GetMapping("/incidents")
  @Operation(summary = "Administrador: consultar incidencias abiertas, con paginación")
  public PageResult<Incident> openIncidents(@AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    return operations.openIncidents(actor(jwt), new PageQuery(page, size));
  }
  private Participant actor(Jwt jwt) {
    return new Participant(Long.valueOf(jwt.getSubject()), Participant.Role.valueOf(jwt.getClaimAsString("role")));
  }
}
