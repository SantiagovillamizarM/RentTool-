package com.renttool.infrastructure.reservation.config;

import com.renttool.application.reservation.port.in.*;
import com.renttool.application.reservation.service.ReservationService;
import com.renttool.application.shared.PageQuery;
import com.renttool.application.shared.PageResult;
import com.renttool.domain.reservation.Participant;
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
  private final TransactionTemplate write;
  private final TransactionTemplate read;

  TransactionalReservations(ReservationService service, PlatformTransactionManager transactions) {
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
  public PageResult<ReservationDetails> list(Participant participant, PageQuery page) {
    return read.execute(s -> service.list(participant, page));
  }

  @Override
  public ReservationDetails accept(Participant provider, Long reservationId) {
    return write.execute(s -> service.accept(provider, reservationId));
  }

  @Override
  public ReservationDetails reject(Participant provider, Long reservationId, String reason) {
    return write.execute(s -> service.reject(provider, reservationId, reason));
  }

  @Override
  public void cancel(Participant participant, Long reservationId) {
    write.executeWithoutResult(s -> service.cancel(participant, reservationId));
  }

  @Override
  public List<HistoryEntry> history(Participant participant, Long reservationId) {
    return read.execute(s -> service.history(participant, reservationId));
  }

  @Override
  public int expireOverdue() {
    return write.execute(s -> service.expireOverdue());
  }
}
