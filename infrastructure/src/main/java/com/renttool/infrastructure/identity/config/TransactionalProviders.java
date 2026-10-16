package com.renttool.infrastructure.identity.config;

import com.renttool.application.identity.port.in.*;
import com.renttool.application.identity.service.ProviderAdministrationService;
import com.renttool.application.shared.PageQuery;
import com.renttool.application.shared.PageResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cuenta y perfil del proveedor se escriben en la misma transacción: si el perfil falla (por
 * ejemplo, otro alta simultánea con el mismo NIT) no queda una cuenta huérfana.
 */
class TransactionalProviders implements ManageProvidersUseCase {
  private final ProviderAdministrationService service;
  private final TransactionTemplate write;
  private final TransactionTemplate read;

  TransactionalProviders(
      ProviderAdministrationService service, PlatformTransactionManager transactions) {
    this.service = service;
    this.write = new TransactionTemplate(transactions);
    this.read = new TransactionTemplate(transactions);
    this.read.setReadOnly(true);
  }

  @Override
  public ProviderDetails register(Long adminId, RegisterCommand command) {
    return write.execute(s -> service.register(adminId, command));
  }

  @Override
  public PageResult<ProviderDetails> list(Long adminId, PageQuery page) {
    return read.execute(s -> service.list(adminId, page));
  }

  @Override
  public ProviderDetails get(Long adminId, Long providerId) {
    return read.execute(s -> service.get(adminId, providerId));
  }

  @Override
  public ProviderDetails update(Long adminId, Long providerId, ProfileCommand command) {
    return write.execute(s -> service.update(adminId, providerId, command));
  }

  @Override
  public ProviderDetails setActive(Long adminId, Long providerId, boolean active) {
    return write.execute(s -> service.setActive(adminId, providerId, active));
  }

  @Override
  public ProviderDetails setLogo(Long adminId, Long providerId, byte[] content) {
    return write.execute(s -> service.setLogo(adminId, providerId, content));
  }

  @Override
  public ProviderDetails removeLogo(Long adminId, Long providerId) {
    return write.execute(s -> service.removeLogo(adminId, providerId));
  }

  @Override
  public void resetPassword(Long adminId, Long providerId, String temporaryPassword) {
    write.executeWithoutResult(s -> service.resetPassword(adminId, providerId, temporaryPassword));
  }
}
