package com.renttool.application.identity.service;

import com.renttool.application.identity.port.in.*;
import com.renttool.application.identity.port.out.AccountRepository;
import com.renttool.application.identity.port.out.ProviderProfileRepository;
import com.renttool.domain.identity.Account;
import com.renttool.domain.identity.ProviderProfile;
import com.renttool.domain.identity.Role;
import com.renttool.domain.shared.exception.AuthenticationFailedException;
import com.renttool.domain.shared.exception.ForbiddenOperationException;
import com.renttool.domain.shared.exception.InvalidInputException;
import java.util.Optional;

public class ProfileService implements GetProfileUseCase, UpdateProfileUseCase {
  private final AccountRepository accounts;
  private final ProviderProfileRepository profiles;

  public ProfileService(AccountRepository accounts, ProviderProfileRepository profiles) {
    this.accounts = accounts;
    this.profiles = profiles;
  }

  @Override
  public Account getActive(Long accountId) {
    return accounts
        .findById(accountId)
        .filter(Account::active)
        .orElseThrow(() -> new AuthenticationFailedException("Cuenta no disponible"));
  }

  @Override
  public Optional<String> logoOf(Long accountId) {
    return profiles.findByAccountId(accountId).map(ProviderProfile::logoLocation);
  }

  @Override
  public Optional<ProviderProfile> providerProfileOf(Long accountId) {
    return profiles.findByAccountId(accountId);
  }

  /**
   * Solo los clientes cambian su nombre. El del administrador es fijo y el del proveedor es su
   * nombre comercial, que gestiona el administrador junto con sus datos legales.
   */
  @Override
  public Account rename(Long accountId, String name) {
    var account = getActive(accountId);
    if (account.isAdministrator())
      throw new ForbiddenOperationException("El nombre del administrador no se puede cambiar");
    if (account.isProvider())
      throw new ForbiddenOperationException(
          "Tu nombre comercial lo actualiza el administrador junto con tus datos legales");
    return accounts.save(account.rename(name));
  }

  @Override
  public Account changePhone(Long accountId, String phone) {
    var account = getActive(accountId);
    if (account.role() != Role.CLIENTE)
      throw new ForbiddenOperationException(
          "El teléfono de proveedores y administrador se gestiona en sus datos de empresa");
    if (phone == null || phone.isBlank())
      throw new InvalidInputException("El teléfono es obligatorio");
    return accounts.save(account.withPhone(phone));
  }
}
