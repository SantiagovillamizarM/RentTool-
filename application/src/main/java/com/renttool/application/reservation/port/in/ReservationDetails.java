package com.renttool.application.reservation.port.in;

import com.renttool.domain.reservation.Reservation;

/** Vista de una reserva con los nombres que necesita mostrar la interfaz. */
public record ReservationDetails(
    Reservation reservation, String toolName, Long providerId, String clientName) {}
