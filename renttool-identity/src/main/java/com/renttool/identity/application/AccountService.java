package com.renttool.identity.application;

import com.renttool.identity.domain.Account;
import com.renttool.shared.BusinessException;
import java.util.Locale;

public class AccountService {
  private final AccountRepository repository;

  public AccountService(AccountRepository repository) {
    this.repository = repository;
  }

  public Account register(String name, String email, String hash, Account.Role role) {
    if (role == Account.Role.ADMINISTRADOR)
      throw new BusinessException(403, "El registro público no permite administradores");
    email = email.strip().toLowerCase(Locale.ROOT);
    if (repository.findByEmail(email).isPresent())
      throw new BusinessException(409, "Este correo ya está registrado");
    return repository.save(new Account(null, name.strip(), email, hash, role, true));
  }

  public Account get(Long id) {
    return repository
        .findById(id)
        .filter(Account::active)
        .orElseThrow(() -> new BusinessException(401, "Cuenta no disponible"));
  }

  public Account update(Long id, String name) {
    Account a = get(id);
    return repository.save(
        new Account(a.id(), name.strip(), a.email(), a.passwordHash(), a.role(), a.active()));
  }
}
