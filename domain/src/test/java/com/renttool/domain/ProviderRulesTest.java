package com.renttool.domain;

import static org.junit.jupiter.api.Assertions.*;

import com.renttool.domain.identity.*;
import com.renttool.domain.shared.exception.InvalidInputException;
import org.junit.jupiter.api.Test;

class ProviderRulesTest {
  @Test
  void verificationDigitFollowsDianAlgorithm() {
    assertEquals(4, Nit.verificationDigitOf("800197268"));
    assertEquals(8, Nit.verificationDigitOf("890903938"));
    assertEquals(1, Nit.verificationDigitOf("899999068"));
    assertEquals("800197268-4", new Nit("800197268", 4).toString());
  }

  @Test
  void nitRejectsTyposAndFormatting() {
    assertThrows(InvalidInputException.class, () -> new Nit("800197268", 5));
    assertThrows(InvalidInputException.class, () -> new Nit("800.197.268", 4));
    assertThrows(InvalidInputException.class, () -> new Nit("1234", 0));
  }

  @Test
  void legalEntityRequiresRepresentative() {
    var nit = new Nit("800197268", 4);
    assertThrows(
        InvalidInputException.class,
        () ->
            new ProviderProfile(
                null, PersonType.JURIDICA, nit, "Obras SAS", null, null, null, "3001234567",
                "Calle 1", "Bogotá", null));
    var person =
        new ProviderProfile(
            null, PersonType.NATURAL, nit, "Ana Ruiz", " ", null, null, "+57 300 123 4567",
            "Calle 1", "Bogotá", "");
    assertNull(person.tradeName());
    assertNull(person.logoLocation());
    assertEquals("Ana Ruiz", person.displayName());
    assertEquals("/uploads/a.png", person.withLogo("/uploads/a.png").logoLocation());
    assertThrows(
        InvalidInputException.class, () -> person.withLogo("https://otro.sitio/logo.png"));
  }

  @Test
  void temporaryPasswordIsClearedWhenUserChoosesOne() {
    var account =
        Account.createWithTemporaryPassword("Obras", "p@obras.co", "hash", Role.PROVEEDOR);
    assertTrue(account.mustChangePassword());
    assertFalse(account.withPassword("otro").mustChangePassword());
    assertTrue(account.withPassword("otro").withTemporaryPassword("x").mustChangePassword());
  }

  @Test
  void removedAccountStaysInactiveForever() {
    var account = new Account(5L, "Obras", "p@obras.co", "hash", Role.PROVEEDOR, true, false, null);
    var removed = account.remove(java.time.Instant.parse("2026-10-15T15:00:00Z"));
    assertTrue(removed.isRemoved());
    assertFalse(removed.active());
    assertEquals("Obras", removed.name(), "conserva sus datos para el historial");
    assertThrows(com.renttool.domain.shared.exception.ConflictException.class, removed::activate);
    assertThrows(
        com.renttool.domain.shared.exception.ConflictException.class,
        () -> removed.remove(java.time.Instant.now()));
  }
}
