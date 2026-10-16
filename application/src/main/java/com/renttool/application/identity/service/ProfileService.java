package com.renttool.application.identity.service;

import com.renttool.application.identity.port.in.*;
import com.renttool.application.identity.port.out.AccountRepository;
import com.renttool.application.identity.port.out.ProviderProfileRepository;
import com.renttool.domain.identity.Account;
import com.renttool.domain.identity.ProviderProfile;
import com.renttool.domain.shared.exception.AuthenticationFailedException;
import com.renttool.domain.shared.exception.ForbiddenOperationException;
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
  public Account rename(Long accountId, String name) {
    var account = getActive(accountId);
    if (account.isAdministrator())
      throw new ForbiddenOperationException("El nombre del administrador no se puede cambiar");
    return accounts.save(account.rename(name));
  }
}
