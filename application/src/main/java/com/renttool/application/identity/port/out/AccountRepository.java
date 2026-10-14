package com.renttool.application.identity.port.out;

import com.renttool.domain.identity.Account;
import java.util.Optional;

public interface AccountRepository {
  Optional<Account> findByEmail(String email);

  Optional<Account> findById(Long id);

  Account save(Account account);
}
