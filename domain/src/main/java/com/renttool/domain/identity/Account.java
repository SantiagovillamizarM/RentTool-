package com.renttool.domain.identity;

import com.renttool.domain.shared.exception.ConflictException;
import com.renttool.domain.shared.exception.InvalidInputException;
import java.time.Instant;
import java.util.Locale;

/**
 * Agregado Usuario: identidad, credencial cifrada, rol y estado. {@code mustChangePassword} marca
 * una contraseña temporal asignada por el administrador, que solo sirve para fijar una propia.
 * {@code removedAt} marca una cuenta eliminada: no se borra para que el historial siga diciendo
 * quién fue, pero no vuelve a activarse. {@code phone} es el teléfono de contacto del cliente, con
 * el que el proveedor coordina pago y entrega; las cuentas anteriores pueden no tenerlo.
 */
public record Account(
    Long id,
    String name,
    String email,
    String passwordHash,
    Role role,
    boolean active,
    boolean mustChangePassword,
    Instant removedAt,
    String phone) {
  public Account {
    if (name == null || name.isBlank()) throw new InvalidInputException("El nombre es obligatorio");
    if (email == null || email.isBlank())
      throw new InvalidInputException("El correo es obligatorio");
    if (passwordHash == null || passwordHash.isBlank())
      throw new InvalidInputException("La contraseña es obligatoria");
    if (role == null) throw new InvalidInputException("El rol es obligatorio");
    name = name.strip();
    email = normalizeEmail(email);
    phone = phone == null || phone.isBlank() ? null : validPhone(phone);
    if (removedAt != null && active)
      throw new InvalidInputException("Una cuenta eliminada no puede estar activa");
  }

  public static Account create(String name, String email, String passwordHash, Role role) {
    return new Account(null, name, email, passwordHash, role, true, false, null, null);
  }

  /** Cliente del registro público: el teléfono es obligatorio para que el proveedor lo contacte. */
  public static Account createClient(String name, String email, String passwordHash, String phone) {
    if (phone == null || phone.isBlank())
      throw new InvalidInputException("El teléfono es obligatorio");
    return new Account(null, name, email, passwordHash, Role.CLIENTE, true, false, null, phone);
  }

  /** Cuenta creada por el administrador: la contraseña inicial es temporal. */
  public static Account createWithTemporaryPassword(
      String name, String email, String passwordHash, Role role) {
    return new Account(null, name, email, passwordHash, role, true, true, null, null);
  }

  public static String normalizeEmail(String email) {
    return email.strip().toLowerCase(Locale.ROOT);
  }

  /** Dígitos, espacios y un + inicial; entre 7 y 20 caracteres. */
  public static String validPhone(String phone) {
    String value = phone.strip();
    if (!value.matches("\\+?[\\d ]{7,20}"))
      throw new InvalidInputException("El teléfono solo admite dígitos, espacios y +, mínimo 7");
    return value;
  }

  public Account rename(String newName) {
    return new Account(
        id, newName, email, passwordHash, role, active, mustChangePassword, removedAt, phone);
  }

  public Account withPhone(String newPhone) {
    return new Account(
        id, name, email, passwordHash, role, active, mustChangePassword, removedAt, newPhone);
  }

  public Account changeEmail(String newEmail) {
    return new Account(
        id, name, newEmail, passwordHash, role, active, mustChangePassword, removedAt, phone);
  }

  public Account deactivate() {
    return new Account(
        id, name, email, passwordHash, role, false, mustChangePassword, removedAt, phone);
  }

  public Account activate() {
    if (isRemoved()) throw new ConflictException("La cuenta fue eliminada y no se puede activar");
    return new Account(id, name, email, passwordHash, role, true, mustChangePassword, null, phone);
  }

  /** Baja definitiva: la cuenta queda inactiva para siempre y conserva sus datos. */
  public Account remove(Instant at) {
    if (isRemoved()) throw new ConflictException("La cuenta ya fue eliminada");
    return new Account(id, name, email, passwordHash, role, false, false, at, phone);
  }

  /** Un cliente aceptado como proveedor: cambia de rol y pasa a mostrarse con su nombre comercial. */
  public Account becomeProvider(String tradeName) {
    if (role != Role.CLIENTE || !active || isRemoved())
      throw new ConflictException("Solo una cuenta de cliente activa puede pasar a proveedor");
    return new Account(
        id, tradeName, email, passwordHash, Role.PROVEEDOR, true, mustChangePassword, null, phone);
  }

  public boolean isRemoved() {
    return removedAt != null;
  }

  /** Contraseña elegida por el propio usuario. */
  public Account withPassword(String newHash) {
    return new Account(id, name, email, newHash, role, active, false, removedAt, phone);
  }

  /** Contraseña asignada por el administrador; el usuario deberá cambiarla al entrar. */
  public Account withTemporaryPassword(String newHash) {
    return new Account(id, name, email, newHash, role, active, true, removedAt, phone);
  }

  public boolean isAdministrator() {
    return role == Role.ADMINISTRADOR;
  }

  public boolean isProvider() {
    return role == Role.PROVEEDOR;
  }
}
