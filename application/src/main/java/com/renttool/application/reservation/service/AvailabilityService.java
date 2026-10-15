package com.renttool.application.reservation.service;

import com.renttool.application.reservation.port.in.CheckAvailabilityUseCase;
import com.renttool.application.reservation.port.out.*;
import com.renttool.domain.reservation.*;
import com.renttool.domain.shared.exception.*;
import java.time.Clock;
import java.time.LocalDate;

public class AvailabilityService implements CheckAvailabilityUseCase {
  private final RentalInventory inventory;
  private final UnitAllocator allocator;
  private final Clock clock;

  public AvailabilityService(
      ReservationRepository reservations, RentalInventory inventory, Clock clock) {
    this.inventory = inventory;
    this.allocator = new UnitAllocator(reservations, inventory);
    this.clock = clock;
  }

  @Override
  public Availability check(Long toolId, RentalPeriod period, int quantity) {
    if (quantity < 1 || quantity > Reservation.MAX_QUANTITY)
      throw new InvalidInputException("La cantidad debe estar entre 1 y " + Reservation.MAX_QUANTITY);
    if (period.startsBefore(LocalDate.now(clock)))
      throw new InvalidInputException("No se puede reservar en fechas pasadas");
    var tool =
        inventory
            .find(toolId)
            .filter(RentableTool::active)
            .orElseThrow(() -> new NotFoundException("Herramienta no encontrada"));
    return new Availability(
        toolId,
        period,
        quantity,
        allocator.freeUnits(toolId, period).size(),
        tool.dailyRate(),
        Reservation.totalFor(tool.dailyRate(), period, quantity));
  }
}
