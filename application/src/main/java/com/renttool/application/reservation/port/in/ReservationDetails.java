package com.renttool.application.reservation.port.in;

import com.renttool.domain.reservation.Reservation;

/**
 * Vista de una reserva con los nombres que necesita mostrar la interfaz. El correo y el teléfono
 * del cliente son para que el proveedor lo contacte por el pago y la entrega; solo los ven quienes
 * pueden ver la reserva (el propio cliente, el proveedor de la herramienta y el administrador).
 */
public record ReservationDetails(
    Reservation reservation,
    String toolName,
    Long providerId,
    String clientName,
    String clientEmail,
    String clientPhone) {}
