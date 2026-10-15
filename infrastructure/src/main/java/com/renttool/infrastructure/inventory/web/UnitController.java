package com.renttool.infrastructure.inventory.web;

import static com.renttool.infrastructure.config.OpenApiConfig.BEARER;

import com.renttool.application.inventory.port.in.ManageUnitsUseCase;
import com.renttool.infrastructure.inventory.web.InventoryDtos.*;
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
@RequestMapping("/api/v1/tools/{toolId}/units")
@Tag(
    name = "Unidades",
    description = "Ejemplares físicos de una herramienta; cada uno con código único y estado")
@SecurityRequirement(name = BEARER)
@ApiResponse(responseCode = "401", description = "Sin token, token vencido o cuenta inactiva")
@ApiResponse(responseCode = "403", description = "No es PROVEEDOR o la herramienta es de otro")
@ApiResponse(responseCode = "404", description = "La herramienta o la unidad no existe")
public class UnitController {
  private final ManageUnitsUseCase units;

  public UnitController(ManageUnitsUseCase units) {
    this.units = units;
  }

  @GetMapping
  @Operation(summary = "Listar las unidades de una herramienta propia")
  @ApiResponse(responseCode = "200", description = "Unidades de la herramienta")
  public List<UnitResponse> list(
      @PathVariable Long toolId, @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return units.list(ActorResolver.from(jwt), toolId).stream().map(UnitResponse::from).toList();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Agregar una unidad")
  @ApiResponse(responseCode = "201", description = "Unidad agregada")
  @ApiResponse(responseCode = "400", description = "Código o estado inválidos")
  @ApiResponse(responseCode = "409", description = "El código ya existe en esta herramienta")
  public UnitResponse add(
      @PathVariable Long toolId,
      @Valid @RequestBody UnitRequest r,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return UnitResponse.from(units.add(ActorResolver.from(jwt), toolId, r.toCommand()));
  }

  @PutMapping("/{unitId}")
  @Operation(summary = "Cambiar el código o el estado de una unidad")
  @ApiResponse(responseCode = "200", description = "Unidad actualizada")
  @ApiResponse(responseCode = "400", description = "Código o estado inválidos")
  @ApiResponse(responseCode = "409", description = "El código ya existe en esta herramienta")
  public UnitResponse change(
      @PathVariable Long toolId,
      @PathVariable Long unitId,
      @Valid @RequestBody UnitRequest r,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return UnitResponse.from(
        units.change(ActorResolver.from(jwt), toolId, unitId, r.toCommand()));
  }

  @DeleteMapping("/{unitId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Retirar una unidad")
  @ApiResponse(responseCode = "204", description = "Unidad retirada")
  public void retire(
      @PathVariable Long toolId,
      @PathVariable Long unitId,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    units.retire(ActorResolver.from(jwt), toolId, unitId);
  }
}
