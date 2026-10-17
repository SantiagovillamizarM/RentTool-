package com.renttool.domain.identity;

import com.renttool.domain.shared.exception.ConflictException;
import com.renttool.domain.shared.exception.InvalidInputException;
import java.time.Instant;

/**
 * Solicitud de un cliente para convertirse en proveedor. El administrador la acepta o la rechaza
 * con un motivo; el solicitante ve la respuesta una vez ({@code notifiedAt}) al volver a entrar.
 */
public record ProviderApplication(
    Long id,
    Long accountId,
    ProviderProfile profile,
    Status status,
    Instant submittedAt,
    Long decidedBy,
    Instant decidedAt,
    String rejectionReason,
    Instant notifiedAt) {
  public static final int MAX_REASON = 500;

  public enum Status {
    PENDIENTE,
    ACEPTADA,
    RECHAZADA
  }

  public ProviderApplication {
    if (accountId == null) throw new InvalidInputException("La solicitud requiere una cuenta");
    if (profile == null) throw new InvalidInputException("Los datos del proveedor son obligatorios");
    if (!accountId.equals(profile.accountId()))
      throw new InvalidInputException("Los datos no corresponden a la cuenta solicitante");
    if (status == null || submittedAt == null)
      throw new InvalidInputException("La solicitud requiere estado y fecha");
    if (status != Status.PENDIENTE && (decidedBy == null || decidedAt == null))
      throw new InvalidInputException("Una solicitud resuelta requiere quién y cuándo");
  }

  public static ProviderApplication submit(Long accountId, ProviderProfile profile, Instant at) {
    return new ProviderApplication(
        null, accountId, profile.withAccount(accountId).withLogo(null), Status.PENDIENTE, at, null,
        null, null, null);
  }

  public ProviderApplication accept(Long adminId, Instant at) {
    ensurePending();
    return new ProviderApplication(
        id, accountId, profile, Status.ACEPTADA, submittedAt, adminId, at, null, null);
  }

  public ProviderApplication reject(Long adminId, Instant at, String reason) {
    ensurePending();
    if (reason == null || reason.isBlank())
      throw new InvalidInputException("Indica el motivo del rechazo para el solicitante");
    if (reason.strip().length() > MAX_REASON)
      throw new InvalidInputException("El motivo admite hasta " + MAX_REASON + " caracteres");
    return new ProviderApplication(
        id, accountId, profile, Status.RECHAZADA, submittedAt, adminId, at, reason.strip(), null);
  }

  /** El solicitante ya vio la respuesta; no se le vuelve a mostrar. */
  public ProviderApplication acknowledge(Instant at) {
    if (!needsNotice()) return this;
    return new ProviderApplication(
        id, accountId, profile, status, submittedAt, decidedBy, decidedAt, rejectionReason, at);
  }

  public boolean isPending() {
    return status == Status.PENDIENTE;
  }

  public boolean needsNotice() {
    return status != Status.PENDIENTE && notifiedAt == null;
  }

  private void ensurePending() {
    if (!isPending()) throw new ConflictException("Esta solicitud ya fue resuelta");
  }
}
