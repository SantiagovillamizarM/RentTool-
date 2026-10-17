package com.renttool.application.identity.service;

import com.renttool.application.identity.port.in.*;
import com.renttool.application.identity.port.in.ManageProvidersUseCase.ProfileCommand;
import com.renttool.application.identity.port.out.*;
import com.renttool.application.shared.PageQuery;
import com.renttool.application.shared.PageResult;
import com.renttool.domain.identity.*;
import com.renttool.domain.identity.ProviderApplication.Status;
import com.renttool.domain.shared.exception.*;
import java.time.Clock;
import java.util.Optional;

/**
 * Solicitudes para ser proveedor. Al aceptar, la cuenta del cliente pasa a proveedor y sus datos
 * legales se guardan como perfil, en la misma transacción que aporta el adaptador.
 */
public class ProviderApplicationService
    implements ApplyAsProviderUseCase, ReviewProviderApplicationsUseCase {
  private final AccountRepository accounts;
  private final ProviderProfileRepository profiles;
  private final ProviderApplicationRepository applications;
  private final Clock clock;

  public ProviderApplicationService(
      AccountRepository accounts,
      ProviderProfileRepository profiles,
      ProviderApplicationRepository applications,
      Clock clock) {
    this.accounts = accounts;
    this.profiles = profiles;
    this.applications = applications;
    this.clock = clock;
  }

  @Override
  public ProviderApplication submit(Long accountId, ProfileCommand command) {
    var account =
        accounts
            .findById(accountId)
            .filter(Account::active)
            .filter(a -> a.role() == Role.CLIENTE)
            .orElseThrow(
                () ->
                    new ForbiddenOperationException(
                        "Solo una cuenta de cliente puede solicitar ser proveedor"));
    boolean pending =
        applications.findLatestByAccount(account.id()).filter(ProviderApplication::isPending)
            .isPresent();
    if (pending) throw new ConflictException("Ya tienes una solicitud en revisión");
    var profile = ProviderProfiles.from(account.id(), command);
    ensureNitIsFree(profile.nit());
    if (applications.existsPendingWithNit(profile.nit().number()))
      throw new ConflictException("Ya hay una solicitud en revisión con el NIT " + profile.nit());
    return applications.save(ProviderApplication.submit(account.id(), profile, clock.instant()));
  }

  @Override
  public Optional<ProviderApplication> current(Long accountId) {
    return applications.findLatestByAccount(accountId);
  }

  @Override
  public void acknowledge(Long accountId) {
    applications
        .findLatestByAccount(accountId)
        .filter(ProviderApplication::needsNotice)
        .ifPresent(a -> applications.save(a.acknowledge(clock.instant())));
  }

  @Override
  public PageResult<ApplicationDetails> list(Long adminId, Status status, PageQuery page) {
    ensureAdministrator(adminId);
    return applications.findByStatus(status, page).map(this::details);
  }

  @Override
  public ApplicationDetails accept(Long adminId, Long applicationId) {
    ensureAdministrator(adminId);
    var application = locked(applicationId);
    if (!application.isPending()) throw new ConflictException("Esta solicitud ya fue resuelta");
    var applicant =
        accounts
            .findById(application.accountId())
            .filter(Account::active)
            .filter(a -> a.role() == Role.CLIENTE)
            .orElseThrow(
                () -> new ConflictException("La cuenta del solicitante ya no está disponible"));
    ensureNitIsFree(application.profile().nit());
    var provider = accounts.save(applicant.becomeProvider(application.profile().displayName()));
    profiles.save(application.profile());
    var accepted = applications.save(application.accept(adminId, clock.instant()));
    return new ApplicationDetails(accepted, provider);
  }

  @Override
  public ApplicationDetails reject(Long adminId, Long applicationId, String reason) {
    ensureAdministrator(adminId);
    var rejected =
        applications.save(locked(applicationId).reject(adminId, clock.instant(), reason));
    return details(rejected);
  }

  private ProviderApplication locked(Long id) {
    return applications
        .findByIdForUpdate(id)
        .orElseThrow(() -> new NotFoundException("Solicitud no encontrada"));
  }

  private ApplicationDetails details(ProviderApplication a) {
    return new ApplicationDetails(a, accounts.findById(a.accountId()).orElse(null));
  }

  private void ensureNitIsFree(Nit nit) {
    if (profiles.findByNit(nit.number()).isPresent())
      throw new ConflictException("Ya existe un proveedor con el NIT " + nit);
  }

  private void ensureAdministrator(Long adminId) {
    accounts
        .findById(adminId)
        .filter(Account::active)
        .filter(Account::isAdministrator)
        .orElseThrow(
            () -> new ForbiddenOperationException("Solo el administrador revisa solicitudes"));
  }
}
