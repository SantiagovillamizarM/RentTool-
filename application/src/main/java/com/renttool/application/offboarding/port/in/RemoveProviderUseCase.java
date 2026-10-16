package com.renttool.application.offboarding.port.in;

/**
 * Baja definitiva de un proveedor que deja la plataforma. Solo el administrador. Toca tres
 * contextos a la vez (cuenta, catálogo y reservas), por eso debe ejecutarse en una sola transacción.
 */
public interface RemoveProviderUseCase {
  /** Lo que pasaría al eliminarlo, sin cambiar nada: es la base del aviso de confirmación. */
  Summary preview(Long adminId, Long providerId);

  /** Elimina la cuenta, retira sus herramientas y cancela sus reservas que aún no empiezan. */
  Summary remove(Long adminId, Long providerId);

  /**
   * @param activeTools herramientas que salen del catálogo público
   * @param pendingRequests solicitudes sin responder que se cancelan
   * @param upcomingRentals reservas aceptadas que aún no empiezan y se cancelan
   * @param rentalsInProgress alquileres ya iniciados: no se cancelan, siguen hasta la devolución
   */
  record Summary(
      Long providerId,
      String providerName,
      String taxId,
      int activeTools,
      int pendingRequests,
      int upcomingRentals,
      int rentalsInProgress) {}
}
