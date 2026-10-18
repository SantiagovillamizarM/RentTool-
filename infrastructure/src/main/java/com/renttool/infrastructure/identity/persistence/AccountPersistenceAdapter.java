package com.renttool.infrastructure.identity.persistence;

import com.renttool.application.identity.port.out.AccountRepository;
import com.renttool.application.shared.PageQuery;
import com.renttool.application.shared.PageResult;
import com.renttool.domain.identity.Account;
import com.renttool.domain.identity.Role;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
  public PageResult<Account> findByRole(Role role, PageQuery page) {
    var p =
        jpa.findByRoleAndRemovedAtIsNull(
            role.name(), PageRequest.of(page.page(), page.size(), Sort.by("id").descending()));
    return new PageResult<>(
        p.getContent().stream().map(this::toDomain).toList(),
        p.getTotalElements(),
        p.getTotalPages(),
        page.page());
  }

  @Override
  public Account save(Account account) {
    return toDomain(jpa.save(toEntity(account)));
  }

  private Account toDomain(AccountEntity e) {
    return new Account(
        e.id,
        e.name,
        e.email,
        e.passwordHash,
        Role.valueOf(e.role),
        e.active,
        e.mustChangePassword,
        e.removedAt == null ? null : e.removedAt.toInstant(ZoneOffset.UTC),
        e.phone);
  }

  private AccountEntity toEntity(Account a) {
    var e = new AccountEntity();
    e.id = a.id();
    e.name = a.name();
    e.email = a.email();
    e.passwordHash = a.passwordHash();
    e.role = a.role().name();
    e.active = a.active();
    e.mustChangePassword = a.mustChangePassword();
    e.removedAt =
        a.removedAt() == null ? null : LocalDateTime.ofInstant(a.removedAt(), ZoneOffset.UTC);
    e.phone = a.phone();
    return e;
  }
}
