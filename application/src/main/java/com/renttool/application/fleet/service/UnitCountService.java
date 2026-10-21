package com.renttool.application.fleet.service;

import com.renttool.application.fleet.port.in.SetUnitCountUseCase;
import com.renttool.application.inventory.port.out.ToolRepository;
import com.renttool.application.inventory.port.out.UnitRepository;
import com.renttool.application.reservation.port.out.ReservationRepository;
import com.renttool.domain.inventory.*;
import com.renttool.domain.reservation.Reservation;
import com.renttool.domain.reservation.ReservationStatus;
import com.renttool.domain.shared.exception.*;
import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Ajusta la cantidad de unidades de una herramienta. Coordina inventario y reservas, por eso vive
 * fuera de ambos contextos. Bloquea la herramienta igual que al aceptar una reserva: mientras se
 * decide qué unidad retirar, nadie puede asignarla. El adaptador aporta la transacción.
 */
public class UnitCountService implements SetUnitCountUseCase {
  private final ToolRepository tools;
  private final UnitRepository units;
  private final ReservationRepository reservations;
  private final Clock clock;
  private final com.renttool.application.operations.port.out.RentalRecords records;

  public UnitCountService(
      ToolRepository tools, UnitRepository units, ReservationRepository reservations, Clock clock,
      com.renttool.application.operations.port.out.RentalRecords records) {
    this.records = records;
    this.tools = tools;
    this.units = units;
    this.reservations = reservations;
    this.clock = clock;
  }

  @Override
  public List<Unit> setCount(Actor actor, Long toolId, int count) {
    if (count < 0 || count > MAX_UNITS)
      throw new InvalidInputException("La cantidad de unidades debe estar entre 0 y " + MAX_UNITS);
    actor.ensureCanManageInventory();
    var tool =
        tools
            .findByIdForUpdate(toolId)
            .orElseThrow(() -> new NotFoundException("Herramienta no encontrada"));
    tool.ensureManageableBy(actor);
    var all = units.findByToolId(toolId);
    var owned = all.stream().filter(u -> u.status() != UnitStatus.RETIRADA).toList();
    if (count > owned.size()) add(tool, all, count - owned.size());
    else if (count < owned.size()) retire(owned, owned.size() - count, toolId);
    return units.findByToolId(toolId).stream()
        .filter(u -> u.status() != UnitStatus.RETIRADA)
        .toList();
  }

  private void add(Tool tool, List<Unit> existing, int howMany) {
    var taken = existing.stream().map(Unit::code).collect(Collectors.toCollection(HashSet::new));
    for (int i = 0; i < howMany; i++) {
      String code = Unit.nextCode(tool.name(), taken);
      taken.add(code);
      units.save(Unit.register(tool.id(), code, UnitStatus.OPERATIVA));
    }
  }

  /** Primero las que están en mantenimiento, luego las más recientes; nunca las comprometidas. */
  private void retire(List<Unit> owned, int howMany, Long toolId) {
    var committed = committedUnits(toolId);
    var free =
        owned.stream()
            .filter(u -> !committed.contains(u.id()))
            .sorted(
                Comparator.comparing((Unit u) -> u.status() != UnitStatus.MANTENIMIENTO)
                    .thenComparing(Unit::id, Comparator.reverseOrder()))
            .toList();
    if (free.size() < howMany) {
      int minimum = owned.size() - free.size();
      throw new ConflictException(
          "Solo puedes bajar hasta "
              + minimum
              + (minimum == 1 ? " unidad" : " unidades")
              + ": las demás están apartadas por reservas pagadas o aceptadas que aún no terminan");
    }
    free.stream().limit(howMany).forEach(u -> units.save(u.retire()));
  }

  private Set<Long> committedUnits(Long toolId) {
    var today = LocalDate.now(clock);
    return reservations.findOpenByTools(List.of(toolId)).stream()
        // Aceptadas o solicitudes ya pagadas: ambas tienen unidades apartadas.
        .filter(r -> !r.unitIds().isEmpty())
        .filter(r -> r.period().end().isAfter(today)
            || records.operation(r.id()).map(operation -> operation.isOut()).orElse(false))
        .map(Reservation::unitIds)
        .flatMap(Set::stream)
        .collect(Collectors.toSet());
  }
}
