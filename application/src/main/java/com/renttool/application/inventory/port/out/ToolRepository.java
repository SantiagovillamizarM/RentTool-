package com.renttool.application.inventory.port.out;

import com.renttool.domain.inventory.Tool;
import com.renttool.application.inventory.port.in.CatalogOrder;
import com.renttool.application.shared.PageQuery;
import com.renttool.application.shared.PageResult;
import java.util.List;
import java.util.Optional;

public interface ToolRepository {
  Optional<Tool> findById(Long id);

  /** Carga la herramienta bloqueándola para escritura hasta el fin de la transacción en curso. */
  Optional<Tool> findByIdForUpdate(Long id);

  /** Todas las herramientas del proveedor, activas o retiradas. */
  List<Long> findIdsByProviderId(Long providerId);

  Tool save(Tool tool);

  /** Publicaciones activas con el orden solicitado, aplicado antes de paginar. */
  PageResult<Tool> searchActive(SearchCriteria criteria, PageQuery page);

  boolean isEmpty();

  /**
   * Filtros opcionales: un valor null no restringe. text busca de forma literal en el nombre y la
   * descripción.
   */
  record SearchCriteria(String text, Long categoryId, Long providerId, CatalogOrder order) {}
}
