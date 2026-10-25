package com.renttool.infrastructure.reservation.external;

import com.renttool.application.inventory.port.out.CategoryRepository;
import com.renttool.application.inventory.port.out.ToolRepository;
import com.renttool.application.inventory.port.out.UnitRepository;
import com.renttool.application.reservation.port.out.RentalInventory;
import com.renttool.domain.inventory.Category;
import com.renttool.domain.inventory.Tool;
import com.renttool.domain.inventory.Unit;
import com.renttool.domain.reservation.RentableTool;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Capa anticorrupción: traduce el inventario a lo que reservas necesita saber. */
@Component
class InventoryRentalAdapter implements RentalInventory {
  private final ToolRepository tools;
  private final UnitRepository units;
  private final CategoryRepository categories;

  InventoryRentalAdapter(ToolRepository tools, UnitRepository units, CategoryRepository categories) {
    this.tools = tools;
    this.units = units;
    this.categories = categories;
  }

  @Override
  public Optional<String> categoryOf(Long toolId) {
    return tools
        .findById(toolId)
        .flatMap(t -> categories.findById(t.categoryId()))
        .map(Category::name);
  }

  @Override
  public Optional<RentableTool> find(Long toolId) {
    return tools.findById(toolId).map(InventoryRentalAdapter::rentable);
  }

  @Override
  public Optional<RentableTool> lockForAllocation(Long toolId) {
    return tools.findByIdForUpdate(toolId).map(InventoryRentalAdapter::rentable);
  }

  @Override
  public List<Long> operationalUnitIds(Long toolId) {
    return units.findByToolId(toolId).stream()
        .filter(Unit::isOperational)
        .map(Unit::id)
        .sorted(Comparator.naturalOrder())
        .toList();
  }

  @Override
  public List<Long> toolIdsOf(Long providerId) {
    return tools.findIdsByProviderId(providerId);
  }

  private static RentableTool rentable(Tool t) {
    return new RentableTool(
        t.id(), t.providerId(), t.name(), t.dailyRate(), t.active(), t.deposit());
  }
}
