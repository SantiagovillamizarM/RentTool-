package com.renttool.application.reservation.port.in;

import com.renttool.application.shared.PageQuery;
import com.renttool.application.shared.PageResult;
import com.renttool.domain.reservation.Participant;
import com.renttool.domain.reservation.ReservationStatus;
import java.time.LocalDate;
import java.util.List;

public interface ManageReservationsUseCase {
  ReservationDetails request(Participant client, RequestCommand command);

  ReservationDetails get(Participant participant, Long reservationId);

  /**
   * Cliente: las suyas. Proveedor: las de sus herramientas. Administrador: todas. {@code status}
   * null incluye todos los estados.
   */
  PageResult<ReservationDetails> list(
      Participant participant, ReservationStatus status, PageQuery page);

  ReservationDetails accept(Participant provider, Long reservationId);

  ReservationDetails reject(Participant provider, Long reservationId, String reason);

  void cancel(Participant participant, Long reservationId);

  /**
   * El cliente amplía la devolución de su reserva aceptada mientras no la haya devuelto. Las mismas
   * unidades deben estar libres en los días nuevos; la diferencia del total queda pendiente de pago.
   */
  ReservationDetails extend(Participant client, Long reservationId, LocalDate newEnd);

  /** Línea de tiempo de la reserva; la ven las mismas personas que pueden verla. */
  List<HistoryEntry> history(Participant participant, Long reservationId);

  record RequestCommand(Long toolId, LocalDate start, LocalDate end, int quantity) {}
}
