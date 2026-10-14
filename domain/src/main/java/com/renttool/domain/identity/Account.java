package com.renttool.domain.identity;

import com.renttool.domain.shared.exception.InvalidInputException;
import java.util.Locale;

/** Agregado Usuario: identidad, credencial cifrada, rol y estado. */
public record Account(
    Long id, String name, String email, String passwordHash, Role role, boolean active) {
  public Account {
    if (name == null || name.isBlank()) throw new InvalidInputException("El nombre es obligatorio");
    if (email == null || email.isBlank())
      throw new InvalidInputException("El correo es obligatorio");
    if (passwordHash == null || passwordHash.isBlank())
      throw new InvalidInputException("La contraseña es obligatoria");
    if (role == null) throw new InvalidInputException("El rol es obligatorio");
    name = name.strip();
    email = normalizeEmail(email);
  }

  public static Account create(String name, String email, String passwordHash, Role role) {
    return new Account(null, name, email, passwordHash, role, true);
  }

  public static String normalizeEmail(String email) {
    return email.strip().toLowerCase(Locale.ROOT);
  }

  public Account rename(String newName) {
    return new Account(id, newName, email, passwordHash, role, active);
  }

  public Account deactivate() {
    return new Account(id, name, email, passwordHash, role, false);
  }

  public boolean isAdministrator() {
    return role == Role.ADMINISTRADOR;
  }
}
