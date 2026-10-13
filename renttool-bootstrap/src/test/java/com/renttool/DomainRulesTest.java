package com.renttool;

import static org.junit.jupiter.api.Assertions.*;

import com.renttool.inventory.application.*;
import com.renttool.inventory.domain.Tool;
import com.renttool.shared.BusinessException;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;

class DomainRulesTest {
  @Test
  void tariffMustBePositive() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new Tool(1L, 1L, 1L, "Taladro", "Detalle", BigDecimal.ZERO, true));
    assertThrows(
        IllegalArgumentException.class,
        () -> new Tool(1L, 1L, 1L, "Taladro", "Detalle", BigDecimal.valueOf(-1), true));
  }

  @Test
  void editingCannotTransferOwnership() {
    var original = new Tool(1L, 10L, 1L, "Taladro", "Detalle", BigDecimal.TEN, true);
    var data = new HashMap<Long, Tool>();
    data.put(1L, original);
    ToolRepository repository =
        new ToolRepository() {
          public Optional<Tool> find(Long id) {
            return Optional.ofNullable(data.get(id));
          }

          public Tool save(Tool tool) {
            data.put(tool.id(), tool);
            return tool;
          }
        };
    var service = new InventoryService(repository);
    var supplied = new Tool(1L, 99L, 1L, "Nuevo nombre", "Detalle", BigDecimal.TEN, true);
    assertEquals(10L, service.save(supplied, 10L, false).providerId());
    assertEquals(
        403,
        assertThrows(BusinessException.class, () -> service.save(supplied, 99L, false)).status());
    service.retire(1L, 10L, false);
    assertFalse(service.get(1L).active());
  }
}
