package com.renttool.infrastructure.inventory.web;

import com.renttool.application.inventory.port.in.BrowseCatalogUseCase;
import com.renttool.infrastructure.inventory.web.InventoryDtos.*;
import com.renttool.application.shared.PageQuery;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class CatalogController {
  private final BrowseCatalogUseCase catalog;

  public CatalogController(BrowseCatalogUseCase catalog) {
    this.catalog = catalog;
  }

  @GetMapping("/categories")
  public List<CategoryResponse> categories() {
    return catalog.categories().stream().map(CategoryResponse::from).toList();
  }

  @GetMapping("/tools")
  public PageResponse<ToolDetailsResponse> search(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(required = false) Long categoryId,
      @RequestParam(defaultValue = "false") boolean mine,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "12") int size,
      @AuthenticationPrincipal Jwt jwt) {
    var query =
        new BrowseCatalogUseCase.Query(
            q, categoryId, mine, ActorResolver.from(jwt), new PageQuery(page, size));
    return PageResponse.from(catalog.search(query).map(ToolDetailsResponse::from));
  }

  @GetMapping("/tools/{id}")
  public ToolDetailsResponse get(@PathVariable Long id) {
    return ToolDetailsResponse.from(catalog.findActive(id));
  }
}
