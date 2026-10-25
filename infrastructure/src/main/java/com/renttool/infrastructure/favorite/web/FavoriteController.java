package com.renttool.infrastructure.favorite.web;

import static com.renttool.infrastructure.config.OpenApiConfig.BEARER;

import com.renttool.application.favorite.port.in.ManageFavoritesUseCase;
import com.renttool.domain.reservation.Participant;
import com.renttool.infrastructure.inventory.web.InventoryDtos.ToolDetailsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/me/favorites")
@Tag(name = "Favoritos", description = "Solo CLIENTE. Herramientas guardadas para reservar después")
@SecurityRequirement(name = BEARER)
@ApiResponse(responseCode = "403", description = "Quien llama no es CLIENTE")
public class FavoriteController {
  private final ManageFavoritesUseCase favorites;

  public FavoriteController(ManageFavoritesUseCase favorites) {
    this.favorites = favorites;
  }

  @GetMapping
  @Operation(summary = "Listar mis favoritos", description = "Solo los que siguen publicados.")
  public List<ToolDetailsResponse> list(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return favorites.list(actor(jwt)).stream().map(ToolDetailsResponse::from).toList();
  }

  @PostMapping("/{toolId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Guardar una herramienta", description = "Si ya estaba guardada no cambia.")
  @ApiResponse(responseCode = "204", description = "Guardada")
  @ApiResponse(responseCode = "404", description = "La herramienta no existe o no está publicada")
  public void add(
      @PathVariable Long toolId, @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    favorites.add(actor(jwt), toolId);
  }

  @DeleteMapping("/{toolId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Quitar una herramienta", description = "Si no estaba guardada no cambia.")
  @ApiResponse(responseCode = "204", description = "Quitada")
  public void remove(
      @PathVariable Long toolId, @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    favorites.remove(actor(jwt), toolId);
  }

  private static Participant actor(Jwt jwt) {
    return new Participant(
        Long.valueOf(jwt.getSubject()), Participant.Role.valueOf(jwt.getClaimAsString("role")));
  }
}
