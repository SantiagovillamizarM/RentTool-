package com.renttool.identity.infrastructure;

import com.renttool.identity.application.AccountRepository;
import com.renttool.identity.domain.Account;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class AccountAdapter implements AccountRepository {
  private final JpaAccounts jpa;

  public AccountAdapter(JpaAccounts jpa) {
    this.jpa = jpa;
  }

  private Account map(AccountEntity e) {
    return new Account(
        e.id, e.name, e.email, e.passwordHash, Account.Role.valueOf(e.role), e.active);
  }

  public Optional<Account> findByEmail(String email) {
    return jpa.findByEmail(email).map(this::map);
  }

  public Optional<Account> findById(Long id) {
    return jpa.findById(id).map(this::map);
  }

  public Account save(Account a) {
    var e = new AccountEntity();
    e.id = a.id();
    e.name = a.name();
    e.email = a.email();
    e.passwordHash = a.passwordHash();
    e.role = a.role().name();
    e.active = a.active();
    return map(jpa.save(e));
  }
}
