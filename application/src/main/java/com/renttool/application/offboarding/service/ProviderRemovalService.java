package com.renttool.application.offboarding.service;

import com.renttool.application.identity.port.out.AccountRepository;
import com.renttool.application.identity.port.out.ProviderProfileRepository;
import com.renttool.application.inventory.port.out.ToolRepository;
import com.renttool.application.offboarding.port.in.RemoveProviderUseCase;
import com.renttool.application.reservation.port.out.ReservationEventRepository;
import com.renttool.application.reservation.port.out.ReservationRepository;
import com.renttool.domain.identity.Account;
import com.renttool.domain.inventory.Tool;
import com.renttool.domain.reservation.Participant;
import com.renttool.domain.reservation.Reservation;
import com.renttool.domain.reservation.ReservationEvent;
import com.renttool.domain.shared.exception.ForbiddenOperationException;
import com.renttool.domain.shared.exception.NotFoundException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Coordina la salida de un proveedor entre identidad, inventario y reservas. Vive fuera de esos
 * contextos para que ninguno dependa de los otros; el adaptador que lo publica aporta la
 * transacción, así una baja a medias nunca queda guardada.
 *
 * <p>Nada se borra: la cuenta queda marcada como eliminada, las herramientas retiradas y las
 * reservas canceladas con su motivo, para que historial e informes sigan diciendo quién fue.
 */
public class ProviderRemovalService implements RemoveProviderUseCase {
  static final String REASON = "El proveedor se retiró de la plataforma";

  private final AccountRepository accounts;
  private final ProviderProfileRepository profiles;
  private final ToolRepository tools;
  private final ReservationRepository reservations;
  private final ReservationEventRepository events;
  private final Clock clock;

  public ProviderRemovalService(
      AccountRepository accounts,
      ProviderProfileRepository profiles,
      ToolRepository tools,
      ReservationRepository reservations,
      ReservationEventRepository events,
      Clock clock) {
    this.accounts = accounts;
    this.profiles = profiles;
    this.tools = tools;
    this.reservations = reservations;
    this.events = events;
    this.clock = clock;
  }

  @Override
  public Summary preview(Long adminId, Long providerId) {
    ensureAdministrator(adminId);
    return summarize(provider(providerId), activeTools(providerId), openReservations(providerId));
  }

  @Override
  public Summary remove(Long adminId, Long providerId) {
    ensureAdministrator(adminId);
    var provider = provider(providerId);
    var now = clock.instant();
    var today = LocalDate.now(clock);
    // Primero la cuenta: desde aquí el proveedor ya no puede aceptar nada.
    accounts.save(provider.remove(now));
    var retiring = activeTools(providerId);
    retiring.forEach(t -> tools.save(t.retire()));
    var open = openReservations(providerId);
    var admin = new Participant(adminId, Participant.Role.ADMINISTRADOR);
    for (var found : open) {
      // Se bloquea antes de cambiarla; pudo cambiar desde la consulta.
      var reservation = reservations.findByIdForUpdate(found.id()).orElseThrow();
      if (!reservation.isCancellableToday(today)) continue;
      var cancelled = reservations.save(reservation.cancelBecause(today, REASON));
      events.append(ReservationEvent.by(admin, cancelled.id(), cancelled.status(), now, REASON));
    }
    return summarize(provider, retiring, open);
  }

  private Summary summarize(Account provider, List<Tool> active, List<Reservation> open) {
    var today = LocalDate.now(clock);
    int pending = 0, upcoming = 0, inProgress = 0;
    for (var r : open) {
      if (r.isPending()) pending++;
      else if (r.isInProgress(today)) inProgress++;
      else upcoming++;
    }
    var taxId =
        profiles.findByAccountId(provider.id()).map(p -> p.nit().toString()).orElse(null);
    return new Summary(
        provider.id(), provider.name(), taxId, active.size(), pending, upcoming, inProgress);
  }

  private void ensureAdministrator(Long adminId) {
    accounts
        .findById(adminId)
        .filter(Account::active)
        .filter(Account::isAdministrator)
        .orElseThrow(
            () -> new ForbiddenOperationException("Solo el administrador elimina proveedores"));
  }

  private Account provider(Long providerId) {
    return accounts
        .findById(providerId)
        .filter(Account::isProvider)
        .filter(a -> !a.isRemoved())
        .orElseThrow(() -> new NotFoundException("Proveedor no encontrado"));
  }

  private List<Tool> activeTools(Long providerId) {
    return tools.findIdsByProviderId(providerId).stream()
        .map(tools::findById)
        .flatMap(Optional::stream)
        .filter(Tool::active)
        .toList();
  }

  private List<Reservation> openReservations(Long providerId) {
    var ids = tools.findIdsByProviderId(providerId);
    return ids.isEmpty() ? List.of() : reservations.findOpenByTools(ids);
  }
}
