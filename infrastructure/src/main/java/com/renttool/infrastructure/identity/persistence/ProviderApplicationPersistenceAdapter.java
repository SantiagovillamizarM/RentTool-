package com.renttool.infrastructure.identity.persistence;

import com.renttool.application.identity.port.out.ProviderApplicationRepository;
import com.renttool.application.shared.PageQuery;
import com.renttool.application.shared.PageResult;
import com.renttool.domain.identity.*;
import com.renttool.domain.identity.ProviderApplication.Status;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
class ProviderApplicationPersistenceAdapter implements ProviderApplicationRepository {
  private final ProviderApplicationJpaRepository jpa;

  ProviderApplicationPersistenceAdapter(ProviderApplicationJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public ProviderApplication save(ProviderApplication application) {
    return toDomain(jpa.save(toEntity(application)));
  }

  @Override
  public Optional<ProviderApplication> findByIdForUpdate(Long id) {
    return jpa.findByIdForUpdate(id).map(ProviderApplicationPersistenceAdapter::toDomain);
  }

  @Override
  public Optional<ProviderApplication> findLatestByAccount(Long accountId) {
    return jpa.findFirstByAccountIdOrderBySubmittedAtDescIdDesc(accountId)
        .map(ProviderApplicationPersistenceAdapter::toDomain);
  }

  @Override
  public boolean existsPendingWithNit(String nit) {
    return jpa.existsByNitAndStatus(nit, Status.PENDIENTE.name());
  }

  @Override
  public PageResult<ProviderApplication> findByStatus(Status status, PageQuery page) {
    var request =
        PageRequest.of(page.page(), page.size(), Sort.by("submittedAt", "id").ascending());
    var p = status == null ? jpa.findAll(request) : jpa.findByStatus(status.name(), request);
    return new PageResult<>(
        p.getContent().stream().map(ProviderApplicationPersistenceAdapter::toDomain).toList(),
        p.getTotalElements(),
        p.getTotalPages(),
        page.page());
  }

  private static LocalDateTime utc(Instant instant) {
    return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
  }

  private static Instant instant(LocalDateTime utc) {
    return utc == null ? null : utc.toInstant(ZoneOffset.UTC);
  }

  private static ProviderApplication toDomain(ProviderApplicationEntity e) {
    var profile =
        new ProviderProfile(
            e.accountId,
            PersonType.valueOf(e.personType),
            new Nit(e.nit, e.verificationDigit),
            e.legalName,
            e.tradeName,
            e.legalRepresentative,
            e.representativeDocument,
            e.phone,
            e.address,
            e.city,
            null);
    return new ProviderApplication(
        e.id,
        e.accountId,
        profile,
        Status.valueOf(e.status),
        instant(e.submittedAt),
        e.decidedBy,
        instant(e.decidedAt),
        e.rejectionReason,
        instant(e.notifiedAt));
  }

  private static ProviderApplicationEntity toEntity(ProviderApplication a) {
    var e = new ProviderApplicationEntity();
    var p = a.profile();
    e.id = a.id();
    e.accountId = a.accountId();
    e.status = a.status().name();
    e.personType = p.personType().name();
    e.nit = p.nit().number();
    e.verificationDigit = p.nit().verificationDigit();
    e.legalName = p.legalName();
    e.tradeName = p.tradeName();
    e.legalRepresentative = p.legalRepresentative();
    e.representativeDocument = p.representativeDocument();
    e.phone = p.phone();
    e.address = p.address();
    e.city = p.city();
    e.submittedAt = utc(a.submittedAt());
    e.decidedBy = a.decidedBy();
    e.decidedAt = utc(a.decidedAt());
    e.rejectionReason = a.rejectionReason();
    e.notifiedAt = utc(a.notifiedAt());
    return e;
  }
}
