package com.renttool.application.inventory.port.in;

import com.renttool.domain.inventory.Actor;
import com.renttool.domain.inventory.Category;
import com.renttool.application.shared.PageQuery;
import com.renttool.application.shared.PageResult;
import java.util.List;

public interface BrowseCatalogUseCase {
  PageResult<ToolDetails> search(Query query);

  ToolDetails findActive(Long toolId);

  List<Category> categories();

  /**
   * onlyMine limita la búsqueda a las publicaciones del actor, que debe ser proveedor. providerId
   * filtra por un proveedor concreto (público); null incluye a todos.
   */
  record Query(
      String text,
      Long categoryId,
      Long providerId,
      boolean onlyMine,
      Actor actor,
      PageQuery page,
      CatalogOrder order) {}
}
