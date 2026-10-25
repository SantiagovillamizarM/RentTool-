package com.renttool.application.operations.service;

import com.renttool.application.operations.port.in.ManageRentalsUseCase;
import com.renttool.application.operations.port.out.RentalRecords;
import com.renttool.application.inventory.port.out.*;
import com.renttool.application.reservation.port.out.ReservationRepository;
import com.renttool.application.billing.port.out.PaymentStore;
import com.renttool.application.shared.*;
import com.renttool.domain.operations.*;
import com.renttool.domain.inventory.*;
import com.renttool.domain.reservation.*;
import com.renttool.domain.billing.Payment;
import com.renttool.domain.shared.exception.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/** Coordina reservas, inventario y pago para registrar movimientos físicos reales. */
public class RentalService implements ManageRentalsUseCase {
  static final String AUTOMATIC = "Entrega automática en la fecha de inicio";

  private final ReservationRepository reservations;
  private final ToolRepository tools;
  private final UnitRepository units;
  private final PaymentStore payments;
  private final RentalRecords records;
  private final Clock clock;

  public RentalService(ReservationRepository reservations, ToolRepository tools,
      UnitRepository units, PaymentStore payments, RentalRecords records, Clock clock) {
    this.reservations = reservations;
    this.tools = tools;
    this.units = units;
    this.payments = payments;
    this.records = records;
    this.clock = clock;
  }

  @Override public Summary get(Participant actor, Long id) {
    var reservation = find(id);
    reservation.ensureVisibleTo(actor, tool(reservation.toolId()).providerId());
    return summary(actor, reservation);
  }

  @Override public Summary deliver(Participant actor, Long id, String notes) {
    var reservation = managed(actor, id);
    if (reservation.status() != ReservationStatus.ACEPTADA)
      throw new ConflictException("Solo se entregan reservas aceptadas");
    if (records.operation(id).isPresent())
      throw new ConflictException("La entrega ya fue registrada");
    if (!paid(id)) throw new ConflictException("La reserva no tiene un pago aprobado");
    var today = LocalDate.now(clock);
    if (today.isBefore(reservation.period().start()) || !today.isBefore(reservation.period().end()))
      throw new ConflictException("La entrega debe registrarse dentro de las fechas del alquiler");
    if (reservation.unitIds().size() != reservation.quantity())
      throw new ConflictException("La reserva no tiene todas sus unidades asignadas");
    var out = records.outUnitIds(reservation.toolId());
    for (Long unitId : reservation.unitIds()) {
      var unit = unit(reservation, unitId);
      if (!unit.isOperational() || out.contains(unitId) || records.hasBlockingIncident(unitId))
        throw new ConflictException("La unidad " + unit.code() + " no está lista para entregar; revisa su devolución y estado");
    }
    records.save(new RentalOperation(id, clock.instant(), actor.id(), notes, null, null, null));
    return summary(actor, reservation);
  }

  @Override public boolean deliverIfStarted(Long id) {
    var reservation = reservations.findByIdForUpdate(id)
        .orElseThrow(() -> new NotFoundException("Reserva no encontrada"));
    var today = LocalDate.now(clock);
    if (reservation.status() != ReservationStatus.ACEPTADA || records.operation(id).isPresent()
        || today.isBefore(reservation.period().start()) || !today.isBefore(reservation.period().end())
        || !paid(id)) return false;
    var tool = tools.findByIdForUpdate(reservation.toolId()).orElseThrow();
    var ready = readyUnits(reservation);
    if (ready.size() < reservation.quantity()) return false;
    if (!ready.equals(reservation.unitIds())) reservations.save(reservation.reassign(ready));
    // El responsable es el proveedor de la herramienta: la entrega es parte de su servicio.
    records.save(new RentalOperation(id, clock.instant(), tool.providerId(), AUTOMATIC, null, null, null));
    return true;
  }

  @Override public List<Long> startedWithoutDelivery() {
    return reservations.findAcceptedUndeliveredStartedBy(LocalDate.now(clock));
  }

  /**
   * Unidades listas para entregar: primero las asignadas que sigan listas y después otras de la
   * herramienta que nadie más tenga apartadas en esas fechas.
   */
  private Set<Long> readyUnits(Reservation reservation) {
    var out = records.outUnitIds(reservation.toolId());
    var heldByOthers = new HashSet<>(reservations.unitsHeldDuring(reservation.toolId(), reservation.period()));
    heldByOthers.removeAll(reservation.unitIds());
    java.util.function.Predicate<Unit> ready = unit -> unit.isOperational()
        && !out.contains(unit.id()) && !records.hasBlockingIncident(unit.id());
    var result = new LinkedHashSet<Long>();
    for (Long unitId : reservation.unitIds())
      units.findById(unitId).filter(ready).ifPresent(unit -> result.add(unit.id()));
    for (var unit : units.findByToolId(reservation.toolId()))
      if (result.size() < reservation.quantity() && !heldByOthers.contains(unit.id()) && ready.test(unit))
        result.add(unit.id());
    return result;
  }

  @Override public Summary returnEquipment(Participant actor, Long id, ReturnCommand command) {
    var reservation = managed(actor, id);
    var operation = records.operation(id)
        .orElseThrow(() -> new ConflictException("Registra primero la entrega"));
    if (!operation.isOut()) throw new ConflictException("La devolución ya fue registrada");
    if (command == null || command.units() == null || command.units().stream().anyMatch(Objects::isNull)
        || command.units().size() != reservation.unitIds().size())
      throw new InvalidInputException("Revisa y devuelve todas las unidades asignadas");
    var returnedIds = command.units().stream().map(ReturnItem::unitId).collect(Collectors.toSet());
    if (!returnedIds.equals(reservation.unitIds()) || returnedIds.size() != command.units().size())
      throw new InvalidInputException("Las unidades devueltas deben coincidir exactamente con las entregadas");

    // Se validan todos los estados antes de cambiar el inventario.
    var items = command.units().stream()
        .map(item -> new UnitReturn(id, item.unitId(), item.condition(), item.notes(), item.charge()))
        .toList();
    // Devolver después de la fecha pactada genera un recargo por cada día de retraso.
    var late = LateFee.of(reservation.period().end(), LocalDate.now(clock),
        reservation.dailyRate(), reservation.quantity());
    records.save(operation.returnEquipment(actor.id(), clock.instant(), command.notes(), late));
    for (var item : items) {
      var unit = unit(reservation, item.unitId());
      records.save(item);
      if (item.condition() != UnitReturn.Condition.BUENO) {
        var type = item.condition() == UnitReturn.Condition.DANADO ? Incident.Type.DANO : Incident.Type.PERDIDA;
        boolean alreadyReported = records.incidents(id).stream()
            .anyMatch(issue -> issue.isOpen() && item.unitId().equals(issue.unitId()) && issue.type() == type);
        if (!alreadyReported)
          records.save(new Incident(null, id, item.unitId(), type, item.notes(),
              actor.id(), clock.instant(), null, null, null));
      }
      UnitStatus next;
      if (item.condition() == UnitReturn.Condition.PERDIDO || unit.status() == UnitStatus.RETIRADA)
        next = UnitStatus.RETIRADA;
      else if (item.condition() == UnitReturn.Condition.DANADO || records.hasBlockingIncident(unit.id()))
        next = UnitStatus.MANTENIMIENTO;
      else next = UnitStatus.OPERATIVA;
      units.save(unit.change(unit.code(), next));
    }
    return summary(actor, reservation);
  }

  @Override public Incident report(Participant actor, Long id, IncidentCommand command) {
    var reservation = reporter(actor, id);
    var operation = records.operation(id)
        .orElseThrow(() -> new ConflictException("Solo se registran incidencias después de la entrega"));
    // El cliente reporta un problema mientras tiene el equipo; después lo revisa el proveedor.
    if (actor.isClient() && !operation.isOut())
      throw new ConflictException("Solo puedes reportar un problema mientras tienes el equipo");
    if (command == null) throw new InvalidInputException("Describe la incidencia");
    if (command.unitId() != null) unit(reservation, command.unitId());
    var incident = new Incident(null, id, command.unitId(), command.type(), command.description(),
        actor.id(), clock.instant(), null, null, null);
    if (incident.blocksUnit() && records.incidents(id).stream().anyMatch(issue ->
        issue.isOpen() && Objects.equals(issue.unitId(), incident.unitId()) && issue.type() == incident.type()))
      throw new ConflictException("Ya hay una incidencia abierta de ese tipo para la unidad");
    var saved = records.save(incident);
    if (saved.blocksUnit()) {
      var unit = unit(reservation, saved.unitId());
      var status = saved.type() == Incident.Type.PERDIDA || unit.status() == UnitStatus.RETIRADA
          ? UnitStatus.RETIRADA : UnitStatus.MANTENIMIENTO;
      units.save(unit.change(unit.code(), status));
    }
    return saved;
  }

  @Override public Incident resolve(Participant actor, Long incidentId, String resolution, boolean readyToUse) {
    var found = records.incident(incidentId).orElseThrow(() -> new NotFoundException("Incidencia no encontrada"));
    var reservation = managed(actor, found.reservationId());
    var current = records.incident(incidentId).orElseThrow();
    if (readyToUse && current.unitId() != null
        && records.outUnitIds(reservation.toolId()).contains(current.unitId()))
      throw new ConflictException("Registra la devolución antes de habilitar la unidad");
    var resolved = records.save(current.resolve(actor.id(), clock.instant(), resolution));
    if (readyToUse && resolved.unitId() != null && !records.hasBlockingIncident(resolved.unitId())) {
      var unit = unit(reservation, resolved.unitId());
      units.save(unit.change(unit.code(), UnitStatus.OPERATIVA));
    }
    return resolved;
  }

  @Override public PageResult<Incident> openIncidents(Participant actor, PageQuery page) {
    if (!actor.isAdministrator()) throw new ForbiddenOperationException("Solo el administrador supervisa todas las incidencias");
    return records.openIncidents(page);
  }

  /** Quien puede reportar: el proveedor de la herramienta, un administrador o el cliente de la reserva. */
  private Reservation reporter(Participant actor, Long id) {
    var reservation = reservations.findByIdForUpdate(id)
        .orElseThrow(() -> new NotFoundException("Reserva no encontrada"));
    if (actor.isClient() && reservation.clientId().equals(actor.id())) return reservation;
    return managed(actor, id);
  }

  private Reservation managed(Participant actor, Long id) {
    var reservation = reservations.findByIdForUpdate(id)
        .orElseThrow(() -> new NotFoundException("Reserva no encontrada"));
    var tool = tools.findByIdForUpdate(reservation.toolId()).orElseThrow();
    if (!actor.isAdministrator() && !(actor.isProvider() && tool.providerId().equals(actor.id())))
      throw new ForbiddenOperationException("Solo el proveedor de la herramienta o un administrador puede registrar la operación");
    return reservation;
  }

  private Summary summary(Participant actor, Reservation reservation) {
    var operation = records.operation(reservation.id()).orElse(null);
    boolean manager = actor.isAdministrator()
        || (actor.isProvider() && tool(reservation.toolId()).providerId().equals(actor.id()));
    boolean paid = paid(reservation.id());
    var today = LocalDate.now(clock);
    boolean canDeliver = manager && paid && operation == null && reservation.status() == ReservationStatus.ACEPTADA
        && !today.isBefore(reservation.period().start()) && today.isBefore(reservation.period().end());
    // La entrega se registra sola el día de inicio; si ya empezó y no hay entrega, es porque
    // ninguna unidad estaba lista: el proveedor o el admin pueden cancelarla con reembolso.
    String status = operation == null
        ? reservation.status() != ReservationStatus.ACEPTADA ? "NO_APLICA"
            : today.isBefore(reservation.period().start()) ? "PENDIENTE_ENTREGA" : "ENTREGA_BLOQUEADA"
        : operation.isOut() ? "EN_USO" : "DEVUELTA";
    var returns = records.returns(reservation.id()).stream()
        .collect(Collectors.toMap(UnitReturn::unitId, item -> item));
    var views = reservation.unitIds().stream().sorted().map(id -> {
      var unit = unit(reservation, id);
      var item = returns.get(id);
      return new UnitView(id, unit.code(), unit.status().name(),
          item == null ? null : item.condition(), item == null ? null : item.notes(),
          item == null ? BigDecimal.ZERO : item.charge());
    }).toList();
    boolean client = actor.isClient() && reservation.clientId().equals(actor.id());
    boolean canReport = operation != null && (manager || (client && operation.isOut()));
    return new Summary(status, manager, paid, canDeliver, canReport, operation, views,
        records.incidents(reservation.id()), charges(reservation, operation, returns.values()));
  }

  private Charges charges(Reservation reservation, RentalOperation operation,
      Collection<UnitReturn> returns) {
    var late = operation == null ? LateFee.NONE : operation.lateFee();
    var damages = returns.stream().map(UnitReturn::charge).reduce(BigDecimal.ZERO, BigDecimal::add);
    // Una extensión sube el total de la reserva; la diferencia con lo pagado queda pendiente.
    var paidAmount = payments.history(reservation.id()).stream()
        .filter(payment -> payment.status() == Payment.Status.APPROVED)
        .map(Payment::amount).findFirst().orElse(null);
    var extension = paidAmount == null ? BigDecimal.ZERO
        : reservation.total().subtract(paidAmount).max(BigDecimal.ZERO);
    var total = late.amount().add(damages).add(extension);
    var deposit = reservation.deposit();
    return new Charges(late.days(), late.amount(), damages, extension, total, deposit,
        deposit.subtract(total).max(BigDecimal.ZERO), total.subtract(deposit).max(BigDecimal.ZERO));
  }

  private boolean paid(Long id) {
    return payments.history(id).stream().anyMatch(payment -> payment.status() == Payment.Status.APPROVED);
  }
  private Reservation find(Long id) {
    return reservations.findById(id).orElseThrow(() -> new NotFoundException("Reserva no encontrada"));
  }
  private Tool tool(Long id) { return tools.findById(id).orElseThrow(() -> new NotFoundException("Herramienta no encontrada")); }
  private Unit unit(Reservation reservation, Long id) {
    if (!reservation.unitIds().contains(id)) throw new InvalidInputException("La unidad no pertenece a esta reserva");
    return units.findById(id).filter(unit -> unit.belongsTo(reservation.toolId()))
        .orElseThrow(() -> new NotFoundException("Unidad no encontrada"));
  }
}
