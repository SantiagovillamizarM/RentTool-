package com.renttool.domain.operations;

import com.renttool.domain.shared.exception.*;
import java.time.Instant;

/**
 * Registro físico de entrega y devolución, separado de la solicitud de reserva. {@code lateFee} es
 * el recargo calculado al devolver; mientras el equipo está fuera vale {@link LateFee#NONE}.
 */
public record RentalOperation(Long reservationId, Instant deliveredAt, Long deliveredBy,
    String deliveryNotes, Instant returnedAt, Long returnedBy, String returnNotes, LateFee lateFee) {
  public RentalOperation {
    if (reservationId == null || deliveredAt == null || deliveredBy == null)
      throw new InvalidInputException("La entrega requiere reserva, fecha y responsable");
    deliveryNotes = notes(deliveryNotes);
    returnNotes = notes(returnNotes);
    if (returnedAt != null && (returnedBy == null || returnedAt.isBefore(deliveredAt)))
      throw new InvalidInputException("La devolución debe ser posterior a la entrega");
    if (lateFee == null) lateFee = LateFee.NONE;
  }

  public RentalOperation(Long reservationId, Instant deliveredAt, Long deliveredBy,
      String deliveryNotes, Instant returnedAt, Long returnedBy, String returnNotes) {
    this(reservationId, deliveredAt, deliveredBy, deliveryNotes, returnedAt, returnedBy, returnNotes,
        LateFee.NONE);
  }

  public RentalOperation returnEquipment(Long actorId, Instant at, String notes) {
    return returnEquipment(actorId, at, notes, LateFee.NONE);
  }

  public RentalOperation returnEquipment(Long actorId, Instant at, String notes, LateFee late) {
    if (returnedAt != null) throw new ConflictException("Esta devolución ya fue registrada");
    return new RentalOperation(reservationId, deliveredAt, deliveredBy, deliveryNotes, at, actorId,
        notes, late);
  }

  public boolean isOut() { return returnedAt == null; }

  private static String notes(String value) {
    if (value != null && value.length() > 500)
      throw new InvalidInputException("Las observaciones admiten hasta 500 caracteres");
    return value == null || value.isBlank() ? null : value.strip();
  }
}
