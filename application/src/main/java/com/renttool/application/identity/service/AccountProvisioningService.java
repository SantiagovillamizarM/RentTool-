package com.renttool.application.identity.service;

import com.renttool.application.identity.port.in.ProvisionAccountUseCase;
import com.renttool.application.identity.port.out.*;
import com.renttool.domain.identity.Account;
import com.renttool.domain.identity.Role;
import com.renttool.domain.shared.exception.*;

public class AccountProvisioningService implements ProvisionAccountUseCase {
  private final AccountRepository accounts;
  private final PasswordHasher passwords;

  public AccountProvisioningService(AccountRepository accounts, PasswordHasher passwords) {
    this.accounts = accounts;
    this.passwords = passwords;
  }

  @Override
  public void provisionAdministrator(String email, String rawPassword) {
    if (rawPassword.length() < 12 || rawPassword.length() > 64)
      throw new InvalidInputException(
          "La contraseña administrativa requiere entre 12 y 64 caracteres");
    String normalized = Account.normalizeEmail(email);
    if (!normalized.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+"))
      throw new InvalidInputException("Correo administrativo inválido");
    var existing = accounts.findByEmail(normalized);
    if (existing.isPresent()) {
      if (!existing.get().isAdministrator())
        throw new ConflictException(
            "El correo configurado ya pertenece a una cuenta no administrativa");
      return;
    }
    accounts.save(
        Account.create(
            "Administrador", normalized, passwords.hash(rawPassword), Role.ADMINISTRADOR));
  }

  @Override
  public Account ensureAccount(String name, String email, String rawPassword, Role role) {
    String normalized = Account.normalizeEmail(email);
    return accounts
        .findByEmail(normalized)
        .orElseGet(
            () ->
                accounts.save(
                    Account.create(name, normalized, passwords.hash(rawPassword), role)));
  }
}
