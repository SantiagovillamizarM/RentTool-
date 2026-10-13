package com.renttool.identity.application;

import com.renttool.identity.domain.Account;
import java.util.Optional;

public interface AccountRepository {
  Optional<Account> findByEmail(String email);

  Optional<Account> findById(Long id);

  Account save(Account account);
}
