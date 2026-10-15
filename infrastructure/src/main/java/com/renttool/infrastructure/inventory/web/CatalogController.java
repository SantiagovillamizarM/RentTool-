package com.renttool.infrastructure.inventory.web;

import static com.renttool.infrastructure.config.OpenApiConfig.BEARER;

import com.renttool.application.inventory.port.in.BrowseCatalogUseCase;
import com.renttool.infrastructure.inventory.web.InventoryDtos.*;
import com.renttool.application.shared.PageQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Catálogo", description = "Consulta pública de categorías y herramientas activas")
public class CatalogController {
  private final BrowseCatalogUseCase catalog;

  public CatalogController(BrowseCatalogUseCase catalog) {
    this.catalog = catalog;
  }

  @GetMapping("/categories")
  @Operation(summary = "Listar categorías")
  @ApiResponse(responseCode = "200", description = "Todas las categorías")
  public List<CategoryResponse> categories() {
    return catalog.categories().stream().map(CategoryResponse::from).toList();
  }

  @GetMapping("/tools")
  @Operation(
      summary = "Buscar herramientas",
      description =
          "Público y paginado. El token solo hace falta con `mine=true`, para que un proveedor"
              + " vea únicamente sus publicaciones.",
      security = @SecurityRequirement(name = BEARER))
  @ApiResponse(responseCode = "200", description = "Página de resultados")
  @ApiResponse(responseCode = "400", description = "Paginación inválida (size entre 1 y 100)")
  @ApiResponse(responseCode = "403", description = "`mine=true` sin rol PROVEEDOR")
  public PageResponse<ToolDetailsResponse> search(
      @Parameter(description = "Texto a buscar en nombre o descripción", example = "taladro")
          @RequestParam(defaultValue = "")
          String q,
      @Parameter(description = "Filtra por categoría", example = "1")
          @RequestParam(required = false)
          Long categoryId,
      @Parameter(description = "Solo las publicaciones del proveedor del token")
          @RequestParam(defaultValue = "false")
          boolean mine,
      @Parameter(description = "Página, desde 0") @RequestParam(defaultValue = "0") int page,
      @Parameter(description = "Tamaño de página, máximo " + PageQuery.MAX_SIZE)
          @RequestParam(defaultValue = "12")
          int size,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    var query =
        new BrowseCatalogUseCase.Query(
            q, categoryId, mine, ActorResolver.from(jwt), new PageQuery(page, size));
    return PageResponse.from(catalog.search(query).map(ToolDetailsResponse::from));
  }

  @GetMapping("/tools/{id}")
  @Operation(summary = "Ver el detalle de una herramienta")
  @ApiResponse(responseCode = "200", description = "Herramienta activa con imágenes y unidades")
  @ApiResponse(responseCode = "404", description = "No existe o fue retirada")
  public ToolDetailsResponse get(@PathVariable Long id) {
    return ToolDetailsResponse.from(catalog.findActive(id));
  }
}
