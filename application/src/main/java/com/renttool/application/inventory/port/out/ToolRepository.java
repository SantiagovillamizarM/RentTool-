package com.renttool.application.inventory.port.out;

import com.renttool.domain.inventory.Tool;
import com.renttool.application.shared.PageQuery;
import com.renttool.application.shared.PageResult;
import java.util.Optional;

public interface ToolRepository {
  Optional<Tool> findById(Long id);

  Tool save(Tool tool);

  /** Publicaciones activas, de la más reciente a la más antigua. */
  PageResult<Tool> searchActive(SearchCriteria criteria, PageQuery page);

  boolean isEmpty();

  /** Filtros opcionales: un valor null no restringe. text busca de forma literal en el nombre. */
  record SearchCriteria(String text, Long categoryId, Long providerId) {}
}
