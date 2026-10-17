package com.renttool.infrastructure.identity.config;

import com.renttool.application.identity.port.in.*;
import com.renttool.application.identity.port.in.ManageProvidersUseCase.ProfileCommand;
import com.renttool.application.identity.service.ProviderApplicationService;
import com.renttool.application.shared.PageQuery;
import com.renttool.application.shared.PageResult;
import com.renttool.domain.identity.ProviderApplication;
import com.renttool.domain.identity.ProviderApplication.Status;
import java.util.Optional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Aceptar cambia el rol de la cuenta, crea el perfil y resuelve la solicitud: o pasa todo o nada.
 * La solicitud se bloquea al leerla, así dos administradores no la resuelven a la vez.
 */
class TransactionalProviderApplications
    implements ApplyAsProviderUseCase, ReviewProviderApplicationsUseCase {
  private final ProviderApplicationService service;
  private final TransactionTemplate write;
  private final TransactionTemplate read;

  TransactionalProviderApplications(
      ProviderApplicationService service, PlatformTransactionManager transactions) {
    this.service = service;
    this.write = new TransactionTemplate(transactions);
    this.write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    this.read = new TransactionTemplate(transactions);
    this.read.setReadOnly(true);
  }

  @Override
  public ProviderApplication submit(Long accountId, ProfileCommand profile) {
    return write.execute(s -> service.submit(accountId, profile));
  }

  @Override
  public Optional<ProviderApplication> current(Long accountId) {
    return read.execute(s -> service.current(accountId));
  }

  @Override
  public void acknowledge(Long accountId) {
    write.executeWithoutResult(s -> service.acknowledge(accountId));
  }

  @Override
  public PageResult<ApplicationDetails> list(Long adminId, Status status, PageQuery page) {
    return read.execute(s -> service.list(adminId, status, page));
  }

  @Override
  public ApplicationDetails accept(Long adminId, Long applicationId) {
    return write.execute(s -> service.accept(adminId, applicationId));
  }

  @Override
  public ApplicationDetails reject(Long adminId, Long applicationId, String reason) {
    return write.execute(s -> service.reject(adminId, applicationId, reason));
  }
}
