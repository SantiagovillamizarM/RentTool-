package com.renttool.application.identity.service;

import com.renttool.application.identity.port.in.ManageProvidersUseCase.ProfileCommand;
import com.renttool.application.identity.port.in.ProvisionAccountUseCase;
import com.renttool.application.identity.port.out.*;
import com.renttool.domain.identity.Account;
import com.renttool.domain.identity.Role;
import com.renttool.domain.shared.exception.*;
import java.time.Clock;
import java.util.Optional;

public class AccountProvisioningService implements ProvisionAccountUseCase {
  private final AccountRepository accounts;
  private final PasswordHasher passwords;
  private final ProviderProfileRepository profiles;
  private final Clock clock;

  public AccountProvisioningService(
      AccountRepository accounts,
      PasswordHasher passwords,
      ProviderProfileRepository profiles,
      Clock clock) {
    this.accounts = accounts;
    this.passwords = passwords;
    this.profiles = profiles;
    this.clock = clock;
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

  @Override
  public Account ensureProvider(String email, String rawPassword, ProfileCommand command) {
    String normalized = Account.normalizeEmail(email);
    var existing = accounts.findByEmail(normalized);
    if (existing.isPresent()) {
      if (!existing.get().isProvider())
        throw new ConflictException("El correo ya pertenece a una cuenta que no es de proveedor");
      return existing.get();
    }
    PasswordPolicy.check(rawPassword);
    var profile = ProviderProfiles.from(null, command);
    // Todo se valida antes de guardar, para no dejar una cuenta sin datos legales.
    if (profiles.findByNit(profile.nit().number()).isPresent())
      throw new ConflictException("Ya existe un proveedor con el NIT " + profile.nit());
    var account =
        accounts.save(
            Account.create(
                profile.displayName(), normalized, passwords.hash(rawPassword), Role.PROVEEDOR));
    profiles.save(profile.withAccount(account.id()));
    return account;
  }

  @Override
  public Optional<Account> findAccount(String email) {
    return accounts.findByEmail(Account.normalizeEmail(email));
  }

  @Override
  public void removeAccount(String email) {
    findAccount(email)
        .filter(a -> !a.isRemoved())
        .ifPresent(a -> accounts.save(a.remove(clock.instant())));
  }
}
