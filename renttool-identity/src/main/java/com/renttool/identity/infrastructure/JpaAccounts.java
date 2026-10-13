package com.renttool.identity.infrastructure;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaAccounts extends JpaRepository<AccountEntity, Long> {
  Optional<AccountEntity> findByEmail(String email);
}
