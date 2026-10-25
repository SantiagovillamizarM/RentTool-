package com.renttool.application.identity.service;

import com.renttool.application.identity.port.in.ManageAdministratorsUseCase;
import com.renttool.application.identity.port.out.AccountRepository;
import com.renttool.application.identity.port.out.PasswordHasher;
import com.renttool.application.shared.PageQuery;
import com.renttool.application.shared.PageResult;
import com.renttool.domain.identity.Account;
import com.renttool.domain.identity.Role;
import com.renttool.domain.shared.exception.*;

/**
 * Un administrador da de alta a otro. La contraseña temporal sigue la regla de las cuentas
 * administrativas (12 a 64 caracteres) y el nuevo debe cambiarla antes de operar.
 */
public class AdministratorManagementService implements ManageAdministratorsUseCase {
  static final int MIN_PASSWORD = 12;

  private final AccountRepository accounts;
  private final PasswordHasher passwords;

  public AdministratorManagementService(AccountRepository accounts, PasswordHasher passwords) {
    this.accounts = accounts;
    this.passwords = passwords;
  }

  @Override
  public PageResult<Account> list(Long adminId, PageQuery page) {
    ensureAdministrator(adminId);
    return accounts.findByRole(Role.ADMINISTRADOR, page);
  }

  @Override
  public Account register(Long adminId, RegisterCommand command) {
    ensureAdministrator(adminId);
    String password = command.temporaryPassword();
    if (password == null || password.length() < MIN_PASSWORD || password.length() > PasswordPolicy.MAX)
      throw new InvalidInputException(
          "La contraseña administrativa requiere entre " + MIN_PASSWORD + " y "
              + PasswordPolicy.MAX + " caracteres");
    if (command.email() == null || command.email().isBlank())
      throw new InvalidInputException("El correo es obligatorio");
    String email = Account.normalizeEmail(command.email());
    if (accounts.findByEmail(email).isPresent())
      throw new ConflictException("Este correo ya está registrado");
    return accounts.save(
        Account.createWithTemporaryPassword(
            command.name(), email, passwords.hash(password), Role.ADMINISTRADOR));
  }

  private void ensureAdministrator(Long adminId) {
    accounts
        .findById(adminId)
        .filter(Account::active)
        .filter(Account::isAdministrator)
        .orElseThrow(
            () -> new ForbiddenOperationException("Solo un administrador agrega administradores"));
  }
}
