package com.renttool.infrastructure.identity.persistence;

import com.renttool.application.identity.port.out.AccountRepository;
import com.renttool.domain.identity.Account;
import com.renttool.domain.identity.Role;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class AccountPersistenceAdapter implements AccountRepository {
  private final AccountJpaRepository jpa;

  AccountPersistenceAdapter(AccountJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public Optional<Account> findByEmail(String email) {
    return jpa.findByEmail(email).map(this::toDomain);
  }

  @Override
  public Optional<Account> findById(Long id) {
    return jpa.findById(id).map(this::toDomain);
  }

  @Override
  public Account save(Account account) {
    return toDomain(jpa.save(toEntity(account)));
  }

  private Account toDomain(AccountEntity e) {
    return new Account(e.id, e.name, e.email, e.passwordHash, Role.valueOf(e.role), e.active);
  }

  private AccountEntity toEntity(Account a) {
    var e = new AccountEntity();
    e.id = a.id();
    e.name = a.name();
    e.email = a.email();
    e.passwordHash = a.passwordHash();
    e.role = a.role().name();
    e.active = a.active();
    return e;
  }
}
