package com.renttool.application.identity.service;

import com.renttool.application.identity.port.in.ManageClientsUseCase;
import com.renttool.application.identity.port.out.AccountRepository;
import com.renttool.application.shared.PageQuery;
import com.renttool.application.shared.PageResult;
import com.renttool.domain.identity.Account;
import com.renttool.domain.identity.Role;
import com.renttool.domain.shared.exception.ForbiddenOperationException;
import com.renttool.domain.shared.exception.NotFoundException;

/** El administrador consulta los clientes registrados y puede cerrarles o devolverles el acceso. */
public class ClientAdministrationService implements ManageClientsUseCase {
  private final AccountRepository accounts;

  public ClientAdministrationService(AccountRepository accounts) {
    this.accounts = accounts;
  }

  @Override
  public PageResult<Account> list(Long adminId, PageQuery page) {
    ensureAdministrator(adminId);
    return accounts.findByRole(Role.CLIENTE, page);
  }

  @Override
  public Account setActive(Long adminId, Long clientId, boolean active) {
    ensureAdministrator(adminId);
    var client =
        accounts
            .findById(clientId)
            .filter(a -> a.role() == Role.CLIENTE)
            .filter(a -> !a.isRemoved())
            .orElseThrow(() -> new NotFoundException("Cliente no encontrado"));
    return accounts.save(active ? client.activate() : client.deactivate());
  }

  private void ensureAdministrator(Long adminId) {
    accounts
        .findById(adminId)
        .filter(Account::active)
        .filter(Account::isAdministrator)
        .orElseThrow(
            () -> new ForbiddenOperationException("Solo el administrador gestiona clientes"));
  }
}
