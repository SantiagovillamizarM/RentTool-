package com.renttool.infrastructure.identity.config;

import com.renttool.application.identity.port.in.ManageClientsUseCase;
import com.renttool.application.identity.service.ClientAdministrationService;
import com.renttool.application.shared.PageQuery;
import com.renttool.application.shared.PageResult;
import com.renttool.domain.identity.Account;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Publica la gestión de clientes con su transacción: lectura para listar, escritura para el estado. */
class TransactionalClients implements ManageClientsUseCase {
  private final ClientAdministrationService service;
  private final TransactionTemplate write;
  private final TransactionTemplate read;

  TransactionalClients(
      ClientAdministrationService service, PlatformTransactionManager transactions) {
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
  public Account setActive(Long adminId, Long clientId, boolean active) {
    return write.execute(s -> service.setActive(adminId, clientId, active));
  }
}
