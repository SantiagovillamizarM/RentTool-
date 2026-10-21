package com.renttool.infrastructure.reservation.persistence;

import com.renttool.application.reservation.port.out.ReservationRepository;
import com.renttool.application.shared.PageQuery;
import com.renttool.application.shared.PageResult;
import com.renttool.domain.reservation.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
class ReservationPersistenceAdapter implements ReservationRepository {
  private static final List<String> HOLDING =
      ReservationStatus.HOLDING_UNITS.stream().map(Enum::name).toList();

  private final ReservationJpaRepository jpa;
  private final java.time.Clock clock;

  ReservationPersistenceAdapter(ReservationJpaRepository jpa, java.time.Clock clock) {
    this.jpa = jpa;
    this.clock = clock;
  }

  @Override
  public Optional<Reservation> findById(Long id) {
    return jpa.findById(id).map(ReservationPersistenceAdapter::toDomain);
  }

  @Override
  public Optional<Reservation> findByIdForUpdate(Long id) {
    return jpa.findByIdForUpdate(id).map(ReservationPersistenceAdapter::toDomain);
  }

  @Override
  public Reservation save(Reservation reservation) {
    return toDomain(jpa.save(toEntity(reservation)));
  }

  @Override
  public Set<Long> unitsHeldDuring(Long toolId, RentalPeriod period) {
    return new HashSet<>(jpa.findHeldUnits(
        toolId, HOLDING, period.start(), period.end(), java.time.LocalDate.now(clock)));
  }

  @Override
  public PageResult<Reservation> findByClient(Long clientId, PageQuery page) {
    return toResult(jpa.findByClientId(clientId, newestFirst(page)), page);
  }

  @Override
  public PageResult<Reservation> findByTools(Collection<Long> toolIds, PageQuery page) {
    return toResult(jpa.findByToolIdIn(toolIds, newestFirst(page)), page);
  }

  @Override
  public PageResult<Reservation> findAll(PageQuery page) {
    return toResult(jpa.findAll(newestFirst(page)), page);
  }

  @Override
  public List<Reservation> findRequestedBetween(
      Instant from, Instant to, ReservationStatus status) {
    var found =
        status == null
            ? jpa.findByRequestedAtGreaterThanEqualAndRequestedAtLessThanOrderByRequestedAtAscIdAsc(
                utc(from), utc(to))
            : jpa
                .findByRequestedAtGreaterThanEqualAndRequestedAtLessThanAndStatusOrderByRequestedAtAscIdAsc(
                    utc(from), utc(to), status.name());
    return found.stream().map(ReservationPersistenceAdapter::toDomain).toList();
  }

  @Override
  public List<Reservation> findOpenByTools(Collection<Long> toolIds) {
    return jpa
        .findByToolIdInAndStatusIn(
            toolIds,
            List.of(ReservationStatus.SOLICITADA.name(), ReservationStatus.ACEPTADA.name()))
        .stream()
        .map(ReservationPersistenceAdapter::toDomain)
        .toList();
  }

  @Override
  public List<Long> findPendingIdsDueBy(Instant now) {
    return jpa.findIdsByStatusDueBy(ReservationStatus.SOLICITADA.name(), utc(now));
  }

  @Override
  public boolean hasDelivery(Long reservationId) {
    return jpa.countDeliveries(reservationId) > 0;
  }

  @Override
  public List<Long> findAcceptedUndeliveredStartedBy(java.time.LocalDate today) {
    return jpa.findAcceptedUndeliveredStartedBy(today);
  }

  private static PageRequest newestFirst(PageQuery page) {
    return PageRequest.of(page.page(), page.size(), Sort.by("id").descending());
  }

  private static PageResult<Reservation> toResult(Page<ReservationEntity> p, PageQuery page) {
    return new PageResult<>(
        p.getContent().stream().map(ReservationPersistenceAdapter::toDomain).toList(),
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

  private static Reservation toDomain(ReservationEntity e) {
    return new Reservation(
        e.id,
        e.clientId,
        e.toolId,
        new RentalPeriod(e.startDate, e.endDate),
        e.quantity,
        e.dailyRate,
        e.total,
        ReservationStatus.valueOf(e.status),
        e.unitIds,
        instant(e.requestedAt),
        instant(e.respondBy),
        e.note);
  }

  private static ReservationEntity toEntity(Reservation r) {
    var e = new ReservationEntity();
    e.id = r.id();
    e.clientId = r.clientId();
    e.toolId = r.toolId();
    e.startDate = r.period().start();
    e.endDate = r.period().end();
    e.quantity = r.quantity();
    e.dailyRate = r.dailyRate();
    e.total = r.total();
    e.status = r.status().name();
    e.requestedAt = utc(r.requestedAt());
    e.respondBy = utc(r.respondBy());
    e.note = r.note();
    e.unitIds = new HashSet<>(r.unitIds());
    return e;
  }
}
