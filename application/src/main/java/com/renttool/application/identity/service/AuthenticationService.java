package com.renttool.application.identity.service;

import com.renttool.application.identity.port.in.*;
import com.renttool.application.identity.port.out.*;
import com.renttool.domain.identity.Account;
import com.renttool.domain.identity.Role;
import com.renttool.domain.shared.exception.*;
import java.time.Clock;
import java.time.Instant;

public class AuthenticationService
    implements RegisterAccountUseCase, LoginUseCase, ChangePasswordUseCase, RefreshSessionUseCase {
  private final AccountRepository accounts;
  private final PasswordHasher passwords;
  private final TokenIssuer tokens;
  private final Clock clock;

  public AuthenticationService(
      AccountRepository accounts, PasswordHasher passwords, TokenIssuer tokens, Clock clock) {
    this.accounts = accounts;
    this.passwords = passwords;
    this.tokens = tokens;
    this.clock = clock;
  }

  @Override
  public AuthenticatedSession register(Command command) {
    if (command.role() != null && command.role() != Role.CLIENTE)
      throw new ForbiddenOperationException(
          "El registro público solo crea clientes. Las cuentas de proveedor las crea el"
              + " administrador");
    String email = Account.normalizeEmail(command.email());
    if (accounts.findByEmail(email).isPresent())
      throw new ConflictException("Este correo ya está registrado");
    var account =
        accounts.save(
            Account.createClient(
                command.name(), email, passwords.hash(command.rawPassword()), command.phone()));
    return session(account, clock.instant());
  }

  @Override
  public AuthenticatedSession login(String email, String rawPassword) {
    var account =
        accounts
            .findByEmail(Account.normalizeEmail(email))
            .filter(Account::active)
            .filter(a -> passwords.matches(rawPassword, a.passwordHash()))
            .orElseThrow(
                () -> new AuthenticationFailedException("Correo o contraseña incorrectos"));
    return session(account, clock.instant());
  }

  @Override
  public AuthenticatedSession refresh(Long accountId, Instant authenticatedAt) {
    var account =
        accounts
            .findById(accountId)
            .filter(Account::active)
            .orElseThrow(() -> new AuthenticationFailedException("Cuenta no disponible"));
    if (!SessionPolicy.canRenew(clock.instant(), authenticatedAt))
      throw new AuthenticationFailedException("La sesión expiró; inicia sesión de nuevo");
    return session(account, authenticatedAt);
  }

  private AuthenticatedSession session(Account account, Instant authenticatedAt) {
    var expiresAt = SessionPolicy.expiryFor(account, clock.instant(), authenticatedAt);
    return new AuthenticatedSession(
        account, tokens.issue(account, authenticatedAt, expiresAt), expiresAt);
  }

  @Override
  public Account changePassword(Long accountId, String currentPassword, String newPassword) {
    var account =
        accounts
            .findById(accountId)
            .filter(Account::active)
            .orElseThrow(() -> new AuthenticationFailedException("Cuenta no disponible"));
    if (!passwords.matches(currentPassword, account.passwordHash()))
      throw new InvalidInputException("La contraseña actual no es correcta");
    PasswordPolicy.check(newPassword);
    if (passwords.matches(newPassword, account.passwordHash()))
      throw new InvalidInputException("La nueva contraseña debe ser distinta de la actual");
    return accounts.save(account.withPassword(passwords.hash(newPassword)));
  }
}
