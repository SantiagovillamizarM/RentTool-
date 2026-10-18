package com.renttool.application.identity.service;

import com.renttool.application.identity.port.in.*;
import com.renttool.application.identity.port.out.*;
import com.renttool.application.shared.PageQuery;
import com.renttool.application.shared.PageResult;
import com.renttool.domain.identity.*;
import com.renttool.domain.shared.exception.*;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * El administrador da de alta proveedores con sus datos legales y una contraseña temporal. La
 * cuenta y el perfil se guardan juntos; el adaptador de entrada aporta la transacción.
 */
public class ProviderAdministrationService
    implements ManageProvidersUseCase, ManageOwnCompanyUseCase {
  public static final int MAX_LOGO_BYTES = 2 * 1024 * 1024;

  private final AccountRepository accounts;
  private final ProviderProfileRepository profiles;
  private final PasswordHasher passwords;
  private final LogoStorage logos;

  public ProviderAdministrationService(
      AccountRepository accounts,
      ProviderProfileRepository profiles,
      PasswordHasher passwords,
      LogoStorage logos) {
    this.accounts = accounts;
    this.profiles = profiles;
    this.passwords = passwords;
    this.logos = logos;
  }

  @Override
  public ProviderDetails register(Long adminId, RegisterCommand command) {
    ensureAdministrator(adminId);
    PasswordPolicy.check(command.temporaryPassword());
    var profile = ProviderProfiles.from(null, command.profile());
    String email = Account.normalizeEmail(command.email());
    var existing = accounts.findByEmail(email);
    if (existing.isPresent())
      throw new ConflictException(
          existing.get().isRemoved()
              ? "Este correo pertenece a un proveedor eliminado; usa otro correo"
              : "Este correo ya está registrado");
    ensureNitIsFree(profile.nit(), null);
    var account =
        accounts.save(
            Account.createWithTemporaryPassword(
                profile.displayName(),
                email,
                passwords.hash(command.temporaryPassword()),
                Role.PROVEEDOR));
    return new ProviderDetails(account, profiles.save(profile.withAccount(account.id())));
  }

  @Override
  public PageResult<ProviderDetails> list(Long adminId, PageQuery page) {
    ensureAdministrator(adminId);
    var result = accounts.findByRole(Role.PROVEEDOR, page);
    var byAccount =
        profiles.findAllByAccountId(result.content().stream().map(Account::id).toList()).stream()
            .collect(Collectors.toMap(ProviderProfile::accountId, Function.identity()));
    return result.map(a -> new ProviderDetails(a, byAccount.get(a.id())));
  }

  @Override
  public ProviderDetails get(Long adminId, Long providerId) {
    ensureAdministrator(adminId);
    return load(providerId);
  }

  @Override
  public ProviderDetails update(Long adminId, Long providerId, ProfileCommand command) {
    ensureAdministrator(adminId);
    var current = load(providerId);
    var profile = ProviderProfiles.from(providerId, command);
    if (current.profile() != null) profile = profile.withLogo(current.profile().logoLocation());
    ensureNitIsFree(profile.nit(), providerId);
    var account = accounts.save(current.account().rename(profile.displayName()));
    return new ProviderDetails(account, profiles.save(profile));
  }

  @Override
  public ProviderDetails setActive(Long adminId, Long providerId, boolean active) {
    ensureAdministrator(adminId);
    var current = load(providerId);
    var account = active ? current.account().activate() : current.account().deactivate();
    return new ProviderDetails(accounts.save(account), current.profile());
  }

  @Override
  public ProviderDetails setLogo(Long adminId, Long providerId, byte[] content) {
    ensureAdministrator(adminId);
    return storeLogo(load(providerId), content);
  }

  @Override
  public ProviderDetails removeLogo(Long adminId, Long providerId) {
    ensureAdministrator(adminId);
    return dropLogo(load(providerId));
  }

  // --- El proveedor sobre su propia empresa ---

  @Override
  public ProviderDetails updateOwn(Long providerId, CompanyCommand c) {
    var current = own(providerId);
    var p = current.profile();
    // NIT y tipo de persona se conservan: solo el administrador los corrige.
    var updated =
        new ProviderProfile(
            providerId,
            p.personType(),
            p.nit(),
            c.legalName(),
            c.tradeName(),
            c.legalRepresentative(),
            c.representativeDocument(),
            c.phone(),
            c.address(),
            c.city(),
            p.logoLocation());
    var account = accounts.save(current.account().rename(updated.displayName()));
    return new ProviderDetails(account, profiles.save(updated));
  }

  @Override
  public ProviderDetails setOwnLogo(Long providerId, byte[] content) {
    return storeLogo(own(providerId), content);
  }

  @Override
  public ProviderDetails removeOwnLogo(Long providerId) {
    return dropLogo(own(providerId));
  }

  @Override
  public List<PublicProvider> publicProviders() {
    var page = accounts.findByRole(Role.PROVEEDOR, new PageQuery(0, PageQuery.MAX_SIZE));
    var active = page.content().stream().filter(Account::active).toList();
    var logos =
        profiles.findAllByAccountId(active.stream().map(Account::id).toList()).stream()
            .filter(p -> p.logoLocation() != null)
            .collect(Collectors.toMap(ProviderProfile::accountId, ProviderProfile::logoLocation));
    return active.stream()
        .map(a -> new PublicProvider(a.id(), a.name(), logos.get(a.id())))
        .sorted(Comparator.comparing(PublicProvider::name, String.CASE_INSENSITIVE_ORDER))
        .toList();
  }

  /** El proveedor activo que edita su propia empresa; debe tener datos legales registrados. */
  private ProviderDetails own(Long providerId) {
    accounts
        .findById(providerId)
        .filter(Account::isProvider)
        .orElseThrow(
            () -> new ForbiddenOperationException("Solo un proveedor edita los datos de su empresa"));
    var current = load(providerId);
    if (!current.account().active())
      throw new ForbiddenOperationException("La cuenta del proveedor no está activa");
    if (current.profile() == null)
      throw new NotFoundException("Tus datos legales aún no están registrados");
    return current;
  }

  private ProviderDetails storeLogo(ProviderDetails current, byte[] content) {
    if (current.profile() == null)
      throw new InvalidInputException("Registra primero los datos legales del proveedor");
    if (content == null || content.length == 0 || content.length > MAX_LOGO_BYTES)
      throw new InvalidInputException("Selecciona un logo PNG o JPEG de hasta 2 MB");
    String location = logos.store(content);
    try {
      var saved = profiles.save(current.profile().withLogo(location));
      discard(current.profile().logoLocation());
      return new ProviderDetails(current.account(), saved);
    } catch (RuntimeException e) {
      logos.delete(location);
      throw e;
    }
  }

  private ProviderDetails dropLogo(ProviderDetails current) {
    if (current.profile() == null || current.profile().logoLocation() == null) return current;
    var saved = profiles.save(current.profile().withLogo(null));
    discard(current.profile().logoLocation());
    return new ProviderDetails(current.account(), saved);
  }

  private void discard(String location) {
    if (location != null) logos.delete(location);
  }

  @Override
  public void resetPassword(Long adminId, Long providerId, String temporaryPassword) {
    ensureAdministrator(adminId);
    PasswordPolicy.check(temporaryPassword);
    var current = load(providerId);
    accounts.save(current.account().withTemporaryPassword(passwords.hash(temporaryPassword)));
  }

  private void ensureAdministrator(Long adminId) {
    accounts
        .findById(adminId)
        .filter(Account::active)
        .filter(Account::isAdministrator)
        .orElseThrow(
            () -> new ForbiddenOperationException("Solo el administrador gestiona proveedores"));
  }

  private ProviderDetails load(Long providerId) {
    var account =
        accounts
            .findById(providerId)
            .filter(Account::isProvider)
            .filter(a -> !a.isRemoved())
            .orElseThrow(() -> new NotFoundException("Proveedor no encontrado"));
    return new ProviderDetails(account, profiles.findByAccountId(providerId).orElse(null));
  }

  private void ensureNitIsFree(Nit nit, Long ownerId) {
    profiles
        .findByNit(nit.number())
        .filter(p -> !p.accountId().equals(ownerId))
        .ifPresent(
            p -> {
              boolean removed = accounts.findById(p.accountId()).map(Account::isRemoved).orElse(false);
              throw new ConflictException(
                  removed
                      ? "El NIT " + nit + " pertenece a un proveedor eliminado"
                      : "Ya existe un proveedor con el NIT " + nit);
            });
  }
}
