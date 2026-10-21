package com.renttool.domain.reservation;

import com.renttool.domain.shared.exception.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

/**
 * Agregado Reserva: un cliente pide unidades de una herramienta para un rango de fechas. Guarda la
 * tarifa del momento de la solicitud para que un cambio posterior en el catálogo no la altere.
 */
public record Reservation(
    Long id,
    Long clientId,
    Long toolId,
    RentalPeriod period,
    int quantity,
    BigDecimal dailyRate,
    BigDecimal total,
    ReservationStatus status,
    Set<Long> unitIds,
    Instant requestedAt,
    Instant respondBy,
    String note) {
  public static final int MAX_QUANTITY = 20;
  public static final int MAX_NOTE = 500;

  public Reservation {
    if (clientId == null || toolId == null)
      throw new InvalidInputException("La reserva requiere cliente y herramienta");
    if (period == null) throw new InvalidInputException("Las fechas son obligatorias");
    if (quantity < 1 || quantity > MAX_QUANTITY)
      throw new InvalidInputException("La cantidad debe estar entre 1 y " + MAX_QUANTITY);
    if (dailyRate == null || dailyRate.signum() <= 0)
      throw new InvalidInputException("La tarifa debe ser positiva");
    if (status == null) throw new InvalidInputException("El estado es obligatorio");
    unitIds = unitIds == null ? Set.of() : Set.copyOf(unitIds);
    if (note != null && note.length() > MAX_NOTE)
      throw new InvalidInputException("El motivo admite hasta " + MAX_NOTE + " caracteres");
  }

  /** Importe del alquiler: tarifa diaria por días cobrados por cantidad de unidades. */
  public static BigDecimal totalFor(BigDecimal dailyRate, RentalPeriod period, int quantity) {
    return dailyRate
        .multiply(BigDecimal.valueOf(period.days()))
        .multiply(BigDecimal.valueOf(quantity))
        .setScale(2, RoundingMode.HALF_UP);
  }

  public static Reservation request(
      Participant client,
      RentableTool tool,
      RentalPeriod period,
      int quantity,
      Instant now,
      Instant respondBy) {
    client.ensureClient();
    if (!tool.active()) throw new ConflictException("La herramienta ya no está publicada");
    return new Reservation(
        null,
        client.id(),
        tool.id(),
        period,
        quantity,
        tool.dailyRate(),
        totalFor(tool.dailyRate(), period, quantity),
        ReservationStatus.SOLICITADA,
        Set.of(),
        now,
        respondBy,
        null);
  }

  /** Al pagarse, la solicitud aparta unidades concretas hasta que el proveedor responda. */
  public Reservation hold(Set<Long> units) {
    ensureStatus(ReservationStatus.SOLICITADA, "Solo se apartan unidades de una solicitud pendiente");
    if (units.size() != quantity)
      throw new ConflictException("Ya no hay unidades disponibles para esas fechas");
    return with(ReservationStatus.SOLICITADA, units, note);
  }

  public boolean holdsUnits() {
    return unitIds.size() == quantity;
  }

  /** Cambia las unidades asignadas, por ejemplo si una entró a mantenimiento antes de la entrega. */
  public Reservation reassign(Set<Long> units) {
    ensureStatus(ReservationStatus.ACEPTADA, "Solo se reasignan unidades de una reserva aceptada");
    if (units.size() != quantity) throw new ConflictException("No hay unidades suficientes para esas fechas");
    return with(ReservationStatus.ACEPTADA, units, note);
  }

  /** El plazo para pagar y responder ya pasó. */
  public boolean isDue(Instant now) {
    return status == ReservationStatus.SOLICITADA && respondBy != null && !respondBy.isAfter(now);
  }

  /**
   * Cancela una reserva aceptada cuyo equipo nunca se entregó, aunque ya haya empezado: por ejemplo
   * porque ninguna unidad estaba disponible o porque terminó el periodo sin entrega.
   */
  public Reservation cancelUndelivered(String reason) {
    ensureStatus(ReservationStatus.ACEPTADA, "Solo se cancela así una reserva aceptada");
    return with(ReservationStatus.CANCELADA, Set.of(), reason);
  }

  /** El proveedor acepta y la reserva queda con unidades concretas asignadas. */
  public Reservation accept(Set<Long> assignedUnits) {
    ensureStatus(ReservationStatus.SOLICITADA, "Solo se puede aceptar una solicitud pendiente");
    if (assignedUnits.size() != quantity)
      throw new ConflictException("No hay unidades suficientes para esas fechas");
    return with(ReservationStatus.ACEPTADA, assignedUnits, null);
  }

  public Reservation reject(String reason) {
    ensureStatus(ReservationStatus.SOLICITADA, "Solo se puede rechazar una solicitud pendiente");
    return with(ReservationStatus.RECHAZADA, Set.of(), reason == null ? null : reason.strip());
  }

  /** Cancela antes del inicio del alquiler y libera las unidades asignadas. */
  public Reservation cancel(LocalDate today) {
    if (status != ReservationStatus.SOLICITADA && status != ReservationStatus.ACEPTADA)
      throw new ConflictException("Esta reserva ya no se puede cancelar");
    boolean started = !today.isBefore(period.start());
    if (status == ReservationStatus.ACEPTADA && started)
      throw new ConflictException("El alquiler ya comenzó; no se puede cancelar");
    return with(ReservationStatus.CANCELADA, Set.of(), note);
  }

  /**
   * Cancelación forzada por el administrador con un motivo, por ejemplo porque el proveedor dejó la
   * plataforma. Sigue las mismas reglas que {@link #cancel}: un alquiler ya iniciado no se toca.
   */
  public Reservation cancelBecause(LocalDate today, String reason) {
    var cancelled = cancel(today);
    return cancelled.with(ReservationStatus.CANCELADA, Set.of(), reason);
  }

  /** Pendiente, o aceptada sin haber empezado: lo que todavía se puede cancelar. */
  public boolean isCancellableToday(LocalDate today) {
    return status == ReservationStatus.SOLICITADA
        || (status == ReservationStatus.ACEPTADA && today.isBefore(period.start()));
  }

  /**
   * Aceptada y con la fecha de inicio ya alcanzada. No garantiza que el equipo se haya entregado:
   * para eso está el registro de entrega.
   */
  public boolean isInProgress(LocalDate today) {
    return status == ReservationStatus.ACEPTADA && !today.isBefore(period.start());
  }

  /** Una solicitud sin respuesta a tiempo deja de estar pendiente. */
  public Reservation expire() {
    ensureStatus(ReservationStatus.SOLICITADA, "Solo vence una solicitud pendiente");
    return with(ReservationStatus.VENCIDA, Set.of(), note);
  }

  public boolean isPending() {
    return status == ReservationStatus.SOLICITADA;
  }

  /** El cliente que la pidió, el proveedor dueño de la herramienta o un administrador. */
  public void ensureVisibleTo(Participant p, Long toolProviderId) {
    p.ensureAuthenticated();
    boolean owner = p.isClient() && clientId.equals(p.id());
    boolean provider = p.isProvider() && toolProviderId.equals(p.id());
    if (!owner && !provider && !p.isAdministrator())
      throw new ForbiddenOperationException("Esta reserva no te pertenece");
  }

  /** Aceptar o rechazar corresponde al proveedor dueño de la herramienta. */
  public void ensureDecidableBy(Participant p, Long toolProviderId) {
    if (!p.isProvider() || !toolProviderId.equals(p.id()))
      throw new ForbiddenOperationException("Solo el proveedor de la herramienta puede decidir");
  }

  /** Cancelar corresponde al cliente que la pidió o a un administrador. */
  public void ensureCancellableBy(Participant p) {
    boolean owner = p.isClient() && clientId.equals(p.id());
    if (!owner && !p.isAdministrator())
      throw new ForbiddenOperationException("Solo el cliente de la reserva puede cancelarla");
  }

  private void ensureStatus(ReservationStatus expected, String message) {
    if (status != expected) throw new ConflictException(message);
  }

  private Reservation with(ReservationStatus newStatus, Set<Long> units, String newNote) {
    return new Reservation(
        id, clientId, toolId, period, quantity, dailyRate, total, newStatus, units, requestedAt,
        newStatus == ReservationStatus.SOLICITADA ? respondBy : null, newNote);
  }
}
