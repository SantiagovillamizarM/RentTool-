package com.renttool.domain.operations;

import com.renttool.domain.shared.exception.*;
import java.time.Instant;

/** Registro físico de entrega y devolución, separado de la solicitud de reserva. */
public record RentalOperation(Long reservationId, Instant deliveredAt, Long deliveredBy,
    String deliveryNotes, Instant returnedAt, Long returnedBy, String returnNotes) {
  public RentalOperation {
    if (reservationId == null || deliveredAt == null || deliveredBy == null)
      throw new InvalidInputException("La entrega requiere reserva, fecha y responsable");
    deliveryNotes = notes(deliveryNotes);
    returnNotes = notes(returnNotes);
    if (returnedAt != null && (returnedBy == null || returnedAt.isBefore(deliveredAt)))
      throw new InvalidInputException("La devolución debe ser posterior a la entrega");
  }

  public RentalOperation returnEquipment(Long actorId, Instant at, String notes) {
    if (returnedAt != null) throw new ConflictException("Esta devolución ya fue registrada");
    return new RentalOperation(reservationId, deliveredAt, deliveredBy, deliveryNotes, at, actorId, notes);
  }

  public boolean isOut() { return returnedAt == null; }

  private static String notes(String value) {
    if (value != null && value.length() > 500)
      throw new InvalidInputException("Las observaciones admiten hasta 500 caracteres");
    return value == null || value.isBlank() ? null : value.strip();
  }
}
