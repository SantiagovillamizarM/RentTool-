package com.renttool.application.identity.service;

import com.renttool.application.identity.port.in.*;
import com.renttool.application.identity.port.out.AccountRepository;
import com.renttool.domain.identity.Account;
import com.renttool.domain.shared.exception.AuthenticationFailedException;

public class ProfileService implements GetProfileUseCase, UpdateProfileUseCase {
  private final AccountRepository accounts;

  public ProfileService(AccountRepository accounts) {
    this.accounts = accounts;
  }

  @Override
  public Account getActive(Long accountId) {
    return accounts
        .findById(accountId)
        .filter(Account::active)
        .orElseThrow(() -> new AuthenticationFailedException("Cuenta no disponible"));
  }

  @Override
  public Account rename(Long accountId, String name) {
    return accounts.save(getActive(accountId).rename(name));
  }
}
