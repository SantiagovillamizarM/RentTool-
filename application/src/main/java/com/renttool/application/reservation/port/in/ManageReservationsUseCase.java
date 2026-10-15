package com.renttool.application.reservation.port.in;

import com.renttool.application.shared.PageQuery;
import com.renttool.application.shared.PageResult;
import com.renttool.domain.reservation.Participant;
import java.time.LocalDate;

public interface ManageReservationsUseCase {
  ReservationDetails request(Participant client, RequestCommand command);

  ReservationDetails get(Participant participant, Long reservationId);

  /** Cliente: las suyas. Proveedor: las de sus herramientas. Administrador: todas. */
  PageResult<ReservationDetails> list(Participant participant, PageQuery page);

  ReservationDetails accept(Participant provider, Long reservationId);

  ReservationDetails reject(Participant provider, Long reservationId, String reason);

  void cancel(Participant participant, Long reservationId);

  record RequestCommand(Long toolId, LocalDate start, LocalDate end, int quantity) {}
}
