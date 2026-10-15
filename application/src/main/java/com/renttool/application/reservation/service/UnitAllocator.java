package com.renttool.application.reservation.service;

import com.renttool.application.reservation.port.out.*;
import com.renttool.domain.reservation.RentalPeriod;
import java.util.List;

/**
 * Regla única de disponibilidad: unidades operativas menos las ocupadas por reservas que se cruzan
 * con el periodo. La usan la consulta pública y la aceptación, para que nunca discrepen.
 */
class UnitAllocator {
  private final ReservationRepository reservations;
  private final RentalInventory inventory;

  UnitAllocator(ReservationRepository reservations, RentalInventory inventory) {
    this.reservations = reservations;
    this.inventory = inventory;
  }

  List<Long> freeUnits(Long toolId, RentalPeriod period) {
    var held = reservations.unitsHeldDuring(toolId, period);
    return inventory.operationalUnitIds(toolId).stream().filter(u -> !held.contains(u)).toList();
  }
}
