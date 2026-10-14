package com.renttool.application.identity.service;

import com.renttool.application.identity.port.in.*;
import com.renttool.application.identity.port.out.*;
import com.renttool.domain.identity.Account;
import com.renttool.domain.identity.Role;
import com.renttool.domain.shared.exception.*;

public class AuthenticationService implements RegisterAccountUseCase, LoginUseCase {
  private final AccountRepository accounts;
  private final PasswordHasher passwords;
  private final TokenIssuer tokens;

  public AuthenticationService(
      AccountRepository accounts, PasswordHasher passwords, TokenIssuer tokens) {
    this.accounts = accounts;
    this.passwords = passwords;
    this.tokens = tokens;
  }

  @Override
  public AuthenticatedSession register(Command command) {
    if (command.role() == Role.ADMINISTRADOR)
      throw new ForbiddenOperationException("El registro público no permite administradores");
    String email = Account.normalizeEmail(command.email());
    if (accounts.findByEmail(email).isPresent())
      throw new ConflictException("Este correo ya está registrado");
    var account =
        accounts.save(
            Account.create(
                command.name(), email, passwords.hash(command.rawPassword()), command.role()));
    return new AuthenticatedSession(account, tokens.issue(account));
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
    return new AuthenticatedSession(account, tokens.issue(account));
  }
}
