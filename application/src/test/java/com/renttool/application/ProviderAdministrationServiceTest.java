package com.renttool.application;

import static org.junit.jupiter.api.Assertions.*;

import com.renttool.application.identity.port.in.*;
import com.renttool.application.identity.port.in.ManageProvidersUseCase.*;
import com.renttool.application.identity.port.out.*;
import com.renttool.application.identity.service.*;
import com.renttool.application.shared.*;
import com.renttool.domain.identity.*;
import com.renttool.domain.shared.exception.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;

/** Alta de proveedores probada con puertos en memoria. */
class ProviderAdministrationServiceTest {
  private final InMemoryAccounts accounts = new InMemoryAccounts();
  private final InMemoryProfiles profiles = new InMemoryProfiles();
  private final PasswordHasher hasher = new PlainHasher();
  private final MutableClock clock = new MutableClock(Instant.parse("2026-10-15T15:00:00Z"));
  private final InMemoryLogos logos = new InMemoryLogos();
  private final ProviderAdministrationService service =
      new ProviderAdministrationService(accounts, profiles, hasher, logos);
  private final AuthenticationService auth =
      new AuthenticationService(
          accounts, hasher, (a, since, until) -> "token-" + a.id(), clock);
  private Long admin, client;

  private static ProfileCommand profile(String nit, int dv) {
    return new ProfileCommand(
        PersonType.JURIDICA, nit, dv, "Obras y Equipos SAS", "ObraYa", "Carlos Pérez", "CC 79123456",
        "+57 601 555 1234", "Calle 80 # 10-20", "Bogotá");
  }

  private final RegisterCommand command =
      new RegisterCommand("Ventas@ObraYa.co", "temporal-123", profile("800197268", 4));

  @BeforeEach
  void accounts() {
    admin = accounts.save(Account.create("Admin", "admin@x.co", "h", Role.ADMINISTRADOR)).id();
    client = accounts.save(Account.create("Cliente", "c@x.co", "h", Role.CLIENTE)).id();
  }

  @Test
  void administratorRegistersProviderWithTemporaryPassword() {
    var created = service.register(admin, command);

    assertEquals(Role.PROVEEDOR, created.account().role());
    assertEquals("ventas@obraya.co", created.account().email());
    assertEquals("ObraYa", created.account().name());
    assertTrue(created.account().mustChangePassword());
    assertEquals(created.account().id(), created.profile().accountId());
    assertEquals(1, service.list(admin, new PageQuery(0, 10)).totalElements());
  }

  @Test
  void onlyAdministratorManagesProviders() {
    assertThrows(ForbiddenOperationException.class, () -> service.register(client, command));
    assertThrows(
        ForbiddenOperationException.class, () -> service.list(client, new PageQuery(0, 10)));
    assertEquals(0, profiles.data.size());
  }

  @Test
  void emailAndNitAreUnique() {
    service.register(admin, command);
    assertThrows(
        ConflictException.class,
        () ->
            service.register(
                admin, new RegisterCommand("ventas@obraya.co", "temporal-123", profile("890903938", 8))));
    assertThrows(
        ConflictException.class,
        () -> service.register(admin, new RegisterCommand("otro@x.co", "temporal-123", command.profile())));
  }

  @Test
  void legacyProviderWithoutProfileIsListedAndCanBeCompleted() {
    var legacy = accounts.save(Account.create("Antiguo", "a@x.co", "h", Role.PROVEEDOR)).id();
    var listed = service.list(admin, new PageQuery(0, 10)).content();
    assertEquals(1, listed.size());
    assertNull(listed.get(0).profile());

    var completed = service.update(admin, legacy, profile("890903938", 8));
    assertEquals("ObraYa", completed.account().name());
    assertEquals(legacy, completed.profile().accountId());
  }

  @Test
  void invalidProfileCreatesNoAccount() {
    var wrongDigit = new RegisterCommand("p@x.co", "temporal-123", profile("800197268", 5));
    assertThrows(InvalidInputException.class, () -> service.register(admin, wrongDigit));
    assertTrue(accounts.findByEmail("p@x.co").isEmpty());
  }

  @Test
  void providerMustReplaceTemporaryPassword() {
    var id = service.register(admin, command).account().id();
    assertThrows(
        InvalidInputException.class, () -> auth.changePassword(id, "incorrecta", "nueva-clave-456"));
    assertFalse(auth.changePassword(id, "temporal-123", "nueva-clave-456").mustChangePassword());

    service.resetPassword(admin, id, "otra-temporal-1");
    assertTrue(accounts.findById(id).orElseThrow().mustChangePassword());
  }

  @Test
  void deactivatedProviderCannotLogIn() {
    var id = service.register(admin, command).account().id();
    service.setActive(admin, id, false);
    assertThrows(
        AuthenticationFailedException.class, () -> auth.login("ventas@obraya.co", "temporal-123"));
    service.setActive(admin, id, true);
    assertNotNull(auth.login("ventas@obraya.co", "temporal-123").token());
  }

  @Test
  void logoIsOptionalReplacedAndKeptOnProfileEdits() {
    var id = service.register(admin, command).account().id();
    assertNull(service.get(admin, id).profile().logoLocation());

    String first = service.setLogo(admin, id, new byte[] {1}).profile().logoLocation();
    String second = service.setLogo(admin, id, new byte[] {2}).profile().logoLocation();
    assertNotEquals(first, second);
    assertEquals(Set.of(second), logos.stored);

    assertEquals(second, service.update(admin, id, profile("800197268", 4)).profile().logoLocation());
    assertNull(service.removeLogo(admin, id).profile().logoLocation());
    assertTrue(logos.stored.isEmpty());
    assertThrows(
        InvalidInputException.class,
        () -> service.setLogo(admin, id, new byte[ProviderAdministrationService.MAX_LOGO_BYTES + 1]));
    assertThrows(ForbiddenOperationException.class, () -> service.setLogo(client, id, new byte[] {1}));
  }

  @Test
  void providerEditsItsCompanyButNotItsNit() {
    var id = service.register(admin, command).account().id();
    var updated =
        service.updateOwn(
            id,
            new ManageOwnCompanyUseCase.CompanyCommand(
                "Obras y Equipos del Norte SAS", "ObraYa Norte", "Lucía Gómez", "CC 1020304050",
                "+57 607 600 0000", "Calle 45 # 20-10", "Bucaramanga"));
    assertEquals("ObraYa Norte", updated.account().name(), "se muestra con el nombre nuevo");
    assertEquals("Lucía Gómez", updated.profile().legalRepresentative());
    assertEquals("800197268", updated.profile().nit().number(), "el NIT no cambia");
    assertEquals(PersonType.JURIDICA, updated.profile().personType());
    assertThrows(
        InvalidInputException.class,
        () ->
            service.updateOwn(
                id,
                new ManageOwnCompanyUseCase.CompanyCommand(
                    "X SAS", null, null, null, "3001234567", "Calle 1", "Cali")),
        "una persona jurídica sigue necesitando representante legal");
  }

  @Test
  void onlyActiveProvidersAreListedForClients() {
    var shown = service.register(admin, command).account().id();
    var hidden =
        service
            .register(admin, new RegisterCommand("b@x.co", "temporal-123", profile("890903938", 8)))
            .account()
            .id();
    service.setActive(admin, hidden, false);
    var listed = service.publicProviders();
    assertEquals(List.of(shown), listed.stream().map(p -> p.id()).toList());
  }

  @Test
  void administratorNameIsFixed() {
    var profiles = new ProfileService(accounts, this.profiles);
    assertThrows(ForbiddenOperationException.class, () -> profiles.rename(admin, "Otro"));
    assertEquals("Nuevo", profiles.rename(client, "Nuevo").name());
    var provider = service.register(admin, command).account().id();
    assertThrows(
        ForbiddenOperationException.class,
        () -> profiles.rename(provider, "Otro nombre"),
        "el nombre comercial lo gestiona el administrador");
  }

  @Test
  void clientAppliesAndAdministratorAcceptsTurningTheAccountIntoAProvider() {
    var applications = new InMemoryApplications();
    var flow = new ProviderApplicationService(accounts, profiles, applications, clock);
    var applied = flow.submit(client, profile("800197268", 4));
    assertTrue(applied.isPending());
    assertThrows(ConflictException.class, () -> flow.submit(client, profile("890903938", 8)));
    assertEquals(1, flow.list(admin, ProviderApplication.Status.PENDIENTE, new PageQuery(0, 10)).totalElements());
    assertThrows(
        ForbiddenOperationException.class,
        () -> flow.accept(client, applied.id()),
        "solo el administrador revisa");

    var accepted = flow.accept(admin, applied.id());
    var account = accounts.findById(client).orElseThrow();
    assertEquals(Role.PROVEEDOR, account.role());
    assertEquals("ObraYa", account.name(), "se muestra con su nombre comercial");
    assertEquals("800197268", profiles.findByAccountId(client).orElseThrow().nit().number());
    assertEquals(ProviderApplication.Status.ACEPTADA, accepted.application().status());
    assertThrows(ConflictException.class, () -> flow.accept(admin, applied.id()));

    assertTrue(flow.current(client).orElseThrow().needsNotice());
    flow.acknowledge(client);
    assertFalse(flow.current(client).orElseThrow().needsNotice(), "el aviso se muestra una vez");
  }

  @Test
  void rejectedApplicantSeesTheReasonAndMayApplyAgain() {
    var applications = new InMemoryApplications();
    var flow = new ProviderApplicationService(accounts, profiles, applications, clock);
    var applied = flow.submit(client, profile("800197268", 4));
    assertThrows(InvalidInputException.class, () -> flow.reject(admin, applied.id(), " "));
    flow.reject(admin, applied.id(), "El RUT no coincide con la razón social");

    var latest = flow.current(client).orElseThrow();
    assertEquals(ProviderApplication.Status.RECHAZADA, latest.status());
    assertEquals("El RUT no coincide con la razón social", latest.rejectionReason());
    assertEquals(Role.CLIENTE, accounts.findById(client).orElseThrow().role());
    assertTrue(flow.submit(client, profile("800197268", 4)).isPending(), "puede volver a intentar");
  }

  @Test
  void applicationsCannotReuseAnExistingProviderNit() {
    service.register(admin, command);
    var flow = new ProviderApplicationService(accounts, profiles, new InMemoryApplications(), clock);
    assertThrows(ConflictException.class, () -> flow.submit(client, profile("800197268", 4)));
    assertThrows(
        ForbiddenOperationException.class,
        () -> flow.submit(admin, profile("890903938", 8)),
        "solo los clientes solicitan");
  }

  @Test
  void publicRegistrationOnlyCreatesClients() {
    assertThrows(
        ForbiddenOperationException.class,
        () ->
            auth.register(
                new RegisterAccountUseCase.Command("P", "p@x.co", "clave-larga-1", Role.PROVEEDOR, "3001234567")));
    var session =
        auth.register(new RegisterAccountUseCase.Command("C", "n@x.co", "clave-larga-1", null, "3001234567"));
    assertEquals(Role.CLIENTE, session.account().role());
    assertEquals("3001234567", session.account().phone());
    assertThrows(
        InvalidInputException.class,
        () -> auth.register(new RegisterAccountUseCase.Command("C", "s@x.co", "clave-larga-1", null, " ")),
        "el teléfono es obligatorio al registrarse");
  }

  private static class PlainHasher implements PasswordHasher {
    public String hash(String raw) {
      return "#" + raw;
    }

    public boolean matches(String raw, String hash) {
      return hash.equals("#" + raw);
    }
  }

  @Test
  void administratorTokensLastTenMinutesAndRenewWithinEightHours() {
    accounts.save(Account.create("Jefe", "jefe@x.co", "#clave-admin-12", Role.ADMINISTRADOR));
    var start = clock.instant();
    var session = auth.login("jefe@x.co", "clave-admin-12");
    assertEquals(start.plus(java.time.Duration.ofMinutes(10)), session.expiresAt());
    assertEquals(
        start.plus(java.time.Duration.ofHours(1)),
        auth.register(new RegisterAccountUseCase.Command("C", "c2@x.co", "clave-larga-1", null, "3001234567"))
            .expiresAt(),
        "los demás roles conservan una hora");

    clock.advance(java.time.Duration.ofMinutes(9));
    var renewed = auth.refresh(session.account().id(), start);
    assertEquals(clock.instant().plus(java.time.Duration.ofMinutes(10)), renewed.expiresAt());

    clock.advance(java.time.Duration.ofHours(7).plusMinutes(45));
    assertEquals(
        start.plus(SessionPolicy.MAX_SESSION),
        auth.refresh(session.account().id(), start).expiresAt(),
        "la última renovación no pasa del tope de 8 horas");
    clock.advance(java.time.Duration.ofMinutes(10));
    assertThrows(
        AuthenticationFailedException.class, () -> auth.refresh(session.account().id(), start));
    assertThrows(
        AuthenticationFailedException.class, () -> auth.refresh(session.account().id(), null));
  }

  private static class MutableClock extends java.time.Clock {
    private Instant now;

    MutableClock(Instant now) {
      this.now = now;
    }

    void advance(java.time.Duration d) {
      now = now.plus(d);
    }

    public java.time.ZoneId getZone() {
      return java.time.ZoneOffset.UTC;
    }

    public java.time.Clock withZone(java.time.ZoneId zone) {
      throw new UnsupportedOperationException();
    }

    public Instant instant() {
      return now;
    }
  }

  private static class InMemoryLogos implements LogoStorage {
    private final Set<String> stored = new HashSet<>();
    private int sequence;

    public String store(byte[] content) {
      String location = "/uploads/logo-" + ++sequence + ".png";
      stored.add(location);
      return location;
    }

    public void delete(String location) {
      stored.remove(location);
    }
  }

  private static class InMemoryAccounts implements AccountRepository {
    private final Map<Long, Account> data = new HashMap<>();
    private long sequence = 1;

    public Optional<Account> findByEmail(String email) {
      return data.values().stream().filter(a -> a.email().equals(email)).findFirst();
    }

    public Optional<Account> findById(Long id) {
      return Optional.ofNullable(data.get(id));
    }

    public PageResult<Account> findByRole(Role role, PageQuery page) {
      var found =
          data.values().stream().filter(a -> a.role() == role && !a.isRemoved()).toList();
      return new PageResult<>(found, found.size(), 1, page.page());
    }

    public Account save(Account a) {
      var saved =
          a.id() != null
              ? a
              : new Account(
                  sequence++, a.name(), a.email(), a.passwordHash(), a.role(), a.active(),
                  a.mustChangePassword(), a.removedAt(), a.phone());
      data.put(saved.id(), saved);
      return saved;
    }
  }

  private static class InMemoryApplications implements ProviderApplicationRepository {
    private final Map<Long, ProviderApplication> data = new LinkedHashMap<>();
    private long sequence = 1;

    public ProviderApplication save(ProviderApplication a) {
      var saved =
          a.id() != null
              ? a
              : new ProviderApplication(
                  sequence++, a.accountId(), a.profile(), a.status(), a.submittedAt(),
                  a.decidedBy(), a.decidedAt(), a.rejectionReason(), a.notifiedAt());
      data.put(saved.id(), saved);
      return saved;
    }

    public Optional<ProviderApplication> findByIdForUpdate(Long id) {
      return Optional.ofNullable(data.get(id));
    }

    public Optional<ProviderApplication> findLatestByAccount(Long accountId) {
      return data.values().stream().filter(a -> a.accountId().equals(accountId)).reduce((x, y) -> y);
    }

    public boolean existsPendingWithNit(String nit) {
      return data.values().stream()
          .anyMatch(a -> a.isPending() && a.profile().nit().number().equals(nit));
    }

    public PageResult<ProviderApplication> findByStatus(
        ProviderApplication.Status status, PageQuery page) {
      var found =
          data.values().stream().filter(a -> status == null || a.status() == status).toList();
      return new PageResult<>(found, found.size(), 1, page.page());
    }
  }

  private static class InMemoryProfiles implements ProviderProfileRepository {
    private final Map<Long, ProviderProfile> data = new LinkedHashMap<>();

    public Optional<ProviderProfile> findByAccountId(Long id) {
      return Optional.ofNullable(data.get(id));
    }

    public Optional<ProviderProfile> findByNit(String nit) {
      return data.values().stream().filter(p -> p.nit().number().equals(nit)).findFirst();
    }

    public List<ProviderProfile> findAllByAccountId(Collection<Long> ids) {
      return ids.stream().map(data::get).filter(Objects::nonNull).toList();
    }

    public ProviderProfile save(ProviderProfile p) {
      data.put(p.accountId(), p);
      return p;
    }
  }
}
