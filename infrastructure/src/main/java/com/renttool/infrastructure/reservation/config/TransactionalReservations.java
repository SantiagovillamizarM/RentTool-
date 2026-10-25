package com.renttool.infrastructure.reservation.config;

import com.renttool.application.reservation.port.in.*;
import com.renttool.application.reservation.service.ReservationService;
import com.renttool.application.shared.PageQuery;
import com.renttool.application.shared.PageResult;
import com.renttool.domain.reservation.Participant;
import com.renttool.domain.reservation.ReservationStatus;
import java.time.LocalDate;
import java.util.List;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Ejecuta cada caso de uso de reservas en su propia transacción. La aplicación no depende de
 * Spring, por eso la frontera transaccional vive en este adaptador.
 *
 * <p>Las escrituras usan READ COMMITTED a propósito. Con REPEATABLE READ, el valor por defecto de
 * MySQL, la transacción que espera el bloqueo de la herramienta seguiría leyendo la foto anterior
 * a que la otra confirmara y vería libre una unidad recién asignada.
 */
class TransactionalReservations implements ManageReservationsUseCase, ExpireReservationsUseCase {
  private final ReservationService service;
  private final com.renttool.application.billing.port.in.ManagePaymentsUseCase payments;
  private final TransactionTemplate write;
  private final TransactionTemplate read;

  TransactionalReservations(ReservationService service, PlatformTransactionManager transactions,
      com.renttool.application.billing.port.in.ManagePaymentsUseCase payments) {
    this.payments = payments;
    this.service = service;
    this.write = new TransactionTemplate(transactions);
    this.write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    this.read = new TransactionTemplate(transactions);
    this.read.setReadOnly(true);
  }

  @Override
  public ReservationDetails request(Participant client, RequestCommand command) {
    return write.execute(s -> service.request(client, command));
  }

  @Override
  public ReservationDetails get(Participant participant, Long reservationId) {
    return read.execute(s -> service.get(participant, reservationId));
  }

  @Override
  public PageResult<ReservationDetails> list(
      Participant participant, ReservationStatus status, PageQuery page) {
    return read.execute(s -> service.list(participant, status, page));
  }

  @Override
  public ReservationDetails accept(Participant provider, Long reservationId) {
    // El cliente paga al solicitar: aceptar confirma una reserva ya pagada. El pago se comprueba
    // después de aceptar, con la reserva ya bloqueada; si falta, la excepción revierte todo. Leerla
    // antes del bloqueo dejaría en caché una copia vieja y dos aceptaciones simultáneas pasarían.
    // Si el plazo ya pasó, se vence (y reembolsa) en su propia transacción antes de intentarlo.
    if (expireIfDue(reservationId))
      throw new com.renttool.domain.shared.exception.ConflictException(
          "El plazo para responder esta solicitud ya venció");
    return write.execute(s -> {
      var accepted = service.accept(provider, reservationId);
      payments.ensurePaid(reservationId);
      return accepted;
    });
  }

  @Override
  public ReservationDetails extend(Participant client, Long reservationId, LocalDate newEnd) {
    return write.execute(s -> service.extend(client, reservationId, newEnd));
  }

  @Override
  public ReservationDetails reject(Participant provider, Long reservationId, String reason) {
    return write.execute(s -> {
      var rejected = service.reject(provider, reservationId, reason);
      payments.refundIfPaid(reservationId, "El proveedor rechazó la solicitud");
      return rejected;
    });
  }

  @Override
  public void cancel(Participant participant, Long reservationId) {
    write.executeWithoutResult(s -> {
      service.cancel(participant, reservationId);
      payments.refundIfPaid(reservationId, "La reserva fue cancelada");
    });
  }

  @Override
  public List<HistoryEntry> history(Participant participant, Long reservationId) {
    return read.execute(s -> service.history(participant, reservationId));
  }

  @Override
  public boolean expireIfDue(Long reservationId) {
    return Boolean.TRUE.equals(write.execute(s -> {
      boolean expired = service.expireIfDue(reservationId);
      if (expired) payments.refundIfPaid(reservationId, "El proveedor no respondió a tiempo");
      return expired;
    }));
  }

  @Override
  public int cancelUndeliveredEnded() {
    return write.execute(s -> {
      var cancelled = service.cancelUndeliveredEndedIds();
      cancelled.forEach(id -> payments.refundIfPaid(id, ReservationService.UNDELIVERED));
      return cancelled.size();
    });
  }

  @Override
  public int expireOverdue() {
    return write.execute(s -> {
      var expired = service.expireOverdueIds();
      expired.forEach(id -> payments.refundIfPaid(id, "El proveedor no respondió a tiempo"));
      return expired.size();
    });
  }
}
