package com.renttool.application;

import static org.junit.jupiter.api.Assertions.*;

import com.renttool.application.inventory.port.in.ManageToolsUseCase;
import com.renttool.application.inventory.port.out.*;
import com.renttool.application.inventory.service.ToolManagementService;
import com.renttool.application.shared.*;
import com.renttool.domain.inventory.*;
import com.renttool.domain.shared.exception.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Caso de uso probado sin Spring ni base de datos: los puertos de salida se simulan en memoria. */
class ToolManagementServiceTest {
  private final InMemoryTools tools = new InMemoryTools();
  private final ToolManagementService service = new ToolManagementService(tools, new OneCategory());
  private final Actor owner = new Actor(10L, true, false);
  private final ManageToolsUseCase.Command command =
      new ManageToolsUseCase.Command(1L, "Nuevo nombre", "Detalle", BigDecimal.TEN);

  @Test
  void editingCannotTransferOwnership() {
    tools.save(new Tool(1L, 10L, 1L, "Taladro", "Detalle", BigDecimal.TEN, true));
    var intruder = new Actor(99L, true, false);

    assertEquals(10L, service.edit(owner, 1L, command).providerId());
    assertThrows(ForbiddenOperationException.class, () -> service.edit(intruder, 1L, command));
    service.retire(owner, 1L);
    assertFalse(tools.findById(1L).orElseThrow().active());
  }

  @Test
  void publishingRequiresExistingCategory() {
    var unknownCategory = new ManageToolsUseCase.Command(7L, "Taladro", "Detalle", BigDecimal.TEN);
    assertThrows(InvalidInputException.class, () -> service.publish(owner, unknownCategory));
  }

  @Test
  void editingMissingToolIsNotFound() {
    assertThrows(NotFoundException.class, () -> service.edit(owner, 42L, command));
  }

  private static class InMemoryTools implements ToolRepository {
    private final Map<Long, Tool> data = new HashMap<>();
    private long sequence = 100;

    public Optional<Tool> findById(Long id) {
      return Optional.ofNullable(data.get(id));
    }

    public Optional<Tool> findByIdForUpdate(Long id) {
      return findById(id);
    }

    public List<Long> findIdsByProviderId(Long providerId) {
      return data.values().stream().filter(t -> t.providerId().equals(providerId)).map(Tool::id).toList();
    }

    public Tool save(Tool tool) {
      var stored =
          tool.id() != null
              ? tool
              : new Tool(
                  ++sequence,
                  tool.providerId(),
                  tool.categoryId(),
                  tool.name(),
                  tool.description(),
                  tool.dailyRate(),
                  tool.active());
      data.put(stored.id(), stored);
      return stored;
    }

    public PageResult<Tool> searchActive(SearchCriteria criteria, PageQuery page) {
      throw new UnsupportedOperationException();
    }

  }

  private static class OneCategory implements CategoryRepository {
    private final Category category = new Category(1L, "Herramientas", "↯");

    public List<Category> findAll() {
      return List.of(category);
    }

    public Optional<Category> findById(Long id) {
      return existsById(id) ? Optional.of(category) : Optional.empty();
    }

    public boolean existsById(Long id) {
      return category.id().equals(id);
    }
  }
}
