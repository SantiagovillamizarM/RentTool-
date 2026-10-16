package com.renttool.infrastructure.identity.persistence;

import com.renttool.application.identity.port.out.ProviderProfileRepository;
import com.renttool.domain.identity.*;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class ProviderProfilePersistenceAdapter implements ProviderProfileRepository {
  private final ProviderProfileJpaRepository jpa;

  ProviderProfilePersistenceAdapter(ProviderProfileJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public Optional<ProviderProfile> findByAccountId(Long accountId) {
    return jpa.findById(accountId).map(this::toDomain);
  }

  @Override
  public List<ProviderProfile> findAllByAccountId(Collection<Long> accountIds) {
    return jpa.findAllById(accountIds).stream().map(this::toDomain).toList();
  }

  @Override
  public Optional<ProviderProfile> findByNit(String nit) {
    return jpa.findByNit(nit).map(this::toDomain);
  }

  @Override
  public ProviderProfile save(ProviderProfile profile) {
    return toDomain(jpa.save(toEntity(profile)));
  }

  private ProviderProfile toDomain(ProviderProfileEntity e) {
    return new ProviderProfile(
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
        e.logoLocation);
  }

  private ProviderProfileEntity toEntity(ProviderProfile p) {
    var e = new ProviderProfileEntity();
    e.accountId = p.accountId();
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
    e.logoLocation = p.logoLocation();
    return e;
  }
}
