package com.renttool.infrastructure.inventory.web;

import static com.renttool.infrastructure.config.OpenApiConfig.BEARER;

import com.renttool.application.inventory.port.in.ManageToolsUseCase;
import com.renttool.infrastructure.inventory.web.InventoryDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tools")
@Tag(name = "Herramientas", description = "Publicación de herramientas por parte del proveedor")
@SecurityRequirement(name = BEARER)
@ApiResponse(responseCode = "401", description = "Sin token, token vencido o cuenta inactiva")
@ApiResponse(responseCode = "403", description = "No es PROVEEDOR o la herramienta es de otro")
public class ToolController {
  private final ManageToolsUseCase tools;

  public ToolController(ManageToolsUseCase tools) {
    this.tools = tools;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Publicar una herramienta")
  @ApiResponse(responseCode = "201", description = "Herramienta publicada")
  @ApiResponse(responseCode = "400", description = "Datos o categoría inválidos")
  public ToolResponse publish(
      @Valid @RequestBody ToolRequest r,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return ToolResponse.from(tools.publish(ActorResolver.from(jwt), r.toCommand()));
  }

  @PutMapping("/{id}")
  @Operation(summary = "Editar una herramienta propia")
  @ApiResponse(responseCode = "200", description = "Herramienta actualizada")
  @ApiResponse(responseCode = "400", description = "Datos o categoría inválidos")
  @ApiResponse(responseCode = "404", description = "La herramienta no existe")
  public ToolResponse edit(
      @PathVariable Long id,
      @Valid @RequestBody ToolRequest r,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return ToolResponse.from(tools.edit(ActorResolver.from(jwt), id, r.toCommand()));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Retirar una herramienta propia",
      description = "Retiro lógico: sale del catálogo pero se conserva su historial.")
  @ApiResponse(responseCode = "204", description = "Herramienta retirada")
  @ApiResponse(responseCode = "404", description = "La herramienta no existe")
  public void retire(
      @PathVariable Long id, @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    tools.retire(ActorResolver.from(jwt), id);
  }
}
