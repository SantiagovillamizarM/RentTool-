package com.renttool.application.identity.port.out;

import com.renttool.application.shared.PageQuery;
import com.renttool.application.shared.PageResult;
import com.renttool.domain.identity.Account;
import com.renttool.domain.identity.Role;
import java.util.Optional;

public interface AccountRepository {
  Optional<Account> findByEmail(String email);

  Optional<Account> findById(Long id);

  /** Cuentas no eliminadas de un rol, de la más reciente a la más antigua. */
  PageResult<Account> findByRole(Role role, PageQuery page);

  Account save(Account account);
}
