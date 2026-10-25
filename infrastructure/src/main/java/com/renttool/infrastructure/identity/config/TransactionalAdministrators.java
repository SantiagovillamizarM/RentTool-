package com.renttool.infrastructure.identity.config;

import com.renttool.application.identity.port.in.ManageAdministratorsUseCase;
import com.renttool.application.identity.service.AdministratorManagementService;
import com.renttool.application.shared.PageQuery;
import com.renttool.application.shared.PageResult;
import com.renttool.domain.identity.Account;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Publica el alta de administradores con su transacción: lectura para listar, escritura para crear. */
class TransactionalAdministrators implements ManageAdministratorsUseCase {
  private final AdministratorManagementService service;
  private final TransactionTemplate write;
  private final TransactionTemplate read;

  TransactionalAdministrators(
      AdministratorManagementService service, PlatformTransactionManager transactions) {
    this.service = service;
    this.write = new TransactionTemplate(transactions);
    this.read = new TransactionTemplate(transactions);
    this.read.setReadOnly(true);
  }

  @Override
  public PageResult<Account> list(Long adminId, PageQuery page) {
    return read.execute(s -> service.list(adminId, page));
  }

  @Override
  public Account register(Long adminId, RegisterCommand command) {
    return write.execute(s -> service.register(adminId, command));
  }
}
