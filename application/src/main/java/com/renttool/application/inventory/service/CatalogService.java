package com.renttool.application.inventory.service;

import com.renttool.application.inventory.port.in.*;
import com.renttool.application.inventory.port.out.*;
import com.renttool.domain.inventory.*;
import com.renttool.application.shared.PageResult;
import com.renttool.domain.shared.exception.NotFoundException;
import java.util.List;

public class CatalogService implements BrowseCatalogUseCase {
  private final ToolRepository tools;
  private final CategoryRepository categories;
  private final UnitRepository units;
  private final ToolImageRepository images;

  public CatalogService(
      ToolRepository tools,
      CategoryRepository categories,
      UnitRepository units,
      ToolImageRepository images) {
    this.tools = tools;
    this.categories = categories;
    this.units = units;
    this.images = images;
  }

  @Override
  public PageResult<ToolDetails> search(Query query) {
    Long owner = null;
    if (query.onlyMine()) {
      query.actor().ensureCanManageInventory();
      owner = query.actor().id();
    }
    String text = query.text() == null || query.text().isBlank() ? null : query.text();
    return tools
        .searchActive(
            new ToolRepository.SearchCriteria(text, query.categoryId(), owner, query.order()), query.page())
        .map(this::details);
  }

  @Override
  public ToolDetails findActive(Long toolId) {
    return tools
        .findById(toolId)
        .filter(Tool::active)
        .map(this::details)
        .orElseThrow(() -> new NotFoundException("Herramienta no encontrada"));
  }

  @Override
  public List<Category> categories() {
    return categories.findAll();
  }

  @Override
  public boolean isInventoryEmpty() {
    return tools.isEmpty();
  }

  private ToolDetails details(Tool tool) {
    return new ToolDetails(
        tool,
        categories.findById(tool.categoryId()).map(Category::name).orElse(""),
        images.findByToolId(tool.id()),
        units.findByToolId(tool.id()).stream().filter(Unit::isOperational).count());
  }
}
