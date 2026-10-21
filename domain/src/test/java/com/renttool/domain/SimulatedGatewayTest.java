package com.renttool.domain;

import com.renttool.domain.billing.SimulatedGateway;
import com.renttool.domain.shared.exception.InvalidInputException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SimulatedGatewayTest {
  final LocalDate today = LocalDate.of(2026, 10, 20);

  @Test void testCardsDecideTheOutcomeAndOnlyTheLastDigitsAreKept() {
    var approved = SimulatedGateway.card("4242 4242 4242 4242", "Ana", 12, 30, "123", today);
    assertTrue(approved.approved());
    assertEquals("Visa •••• 4242", approved.detail());
    var mastercard = SimulatedGateway.card("5555-5555-5555-4444", "Ana", 1, 2027, "123", today);
    assertEquals("Mastercard •••• 4444", mastercard.detail());
    var declined = SimulatedGateway.card("4000000000000002", "Ana", 10, 2026, "1234", today);
    assertFalse(declined.approved());
    assertEquals("Fondos insuficientes", declined.message());
  }

  @Test void invalidCardDataIsRejectedLikeABankWould() {
    assertThrows(InvalidInputException.class, () -> SimulatedGateway.card("4242424242424241", "Ana", 12, 30, "123", today));
    assertThrows(InvalidInputException.class, () -> SimulatedGateway.card("4242424242424242", " ", 12, 30, "123", today));
    assertThrows(InvalidInputException.class, () -> SimulatedGateway.card("4242424242424242", "Ana", 9, 2026, "123", today));
    assertThrows(InvalidInputException.class, () -> SimulatedGateway.card("4242424242424242", "Ana", 13, 2030, "123", today));
    assertThrows(InvalidInputException.class, () -> SimulatedGateway.card("4242424242424242", "Ana", 12, 2030, "12", today));
    // Una tarjeta válida que no es de prueba no se acepta: la simulación no debe recibir tarjetas reales.
    assertThrows(InvalidInputException.class, () -> SimulatedGateway.card("4111111111111111", "Ana", 12, 2030, "123", today));
  }

  @Test void nequiApprovesAnyValidPhoneExceptTheDeclinedTestNumber() {
    var approved = SimulatedGateway.nequi("300 123 4567");
    assertTrue(approved.approved());
    assertEquals("Nequi •••• 4567", approved.detail());
    assertFalse(SimulatedGateway.nequi(SimulatedGateway.NEQUI_DECLINED).approved());
    assertThrows(InvalidInputException.class, () -> SimulatedGateway.nequi("2001234567"));
    assertThrows(InvalidInputException.class, () -> SimulatedGateway.nequi("30012"));
  }
}
