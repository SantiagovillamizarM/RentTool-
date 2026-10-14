package com.renttool.domain;

import static org.junit.jupiter.api.Assertions.*;

import com.renttool.domain.inventory.*;
import com.renttool.domain.shared.exception.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DomainRulesTest {
  @Test
  void tariffMustBePositive() {
    assertThrows(
        InvalidInputException.class,
        () -> new Tool(1L, 1L, 1L, "Taladro", "Detalle", BigDecimal.ZERO, true));
    assertThrows(
        InvalidInputException.class,
        () -> new Tool(1L, 1L, 1L, "Taladro", "Detalle", BigDecimal.valueOf(-1), true));
  }

  @Test
  void onlyProvidersCanPublish() {
    var client = new Actor(5L, false, false);
    assertThrows(
        ForbiddenOperationException.class,
        () -> Tool.publish(client, 1L, "Taladro", "Detalle", BigDecimal.TEN));
  }

  @Test
  void onlyOwnerOrAdministratorCanManageTool() {
    var tool = new Tool(1L, 10L, 1L, "Taladro", "Detalle", BigDecimal.TEN, true);
    assertDoesNotThrow(() -> tool.ensureManageableBy(new Actor(10L, true, false)));
    assertDoesNotThrow(() -> tool.ensureManageableBy(new Actor(1L, false, true)));
    assertThrows(
        ForbiddenOperationException.class,
        () -> tool.ensureManageableBy(new Actor(99L, true, false)));
  }

  @Test
  void imageLocationMustBeHttpsOrInternal() {
    assertThrows(
        InvalidInputException.class, () -> ToolImage.attach(1L, "javascript:alert(1)"));
    assertDoesNotThrow(() -> ToolImage.attach(1L, "https://example.com/a.png"));
  }
}
