package com.renttool.application.reservation.port.in;

/**
 * Aparta unidades para una solicitud que se está pagando. No abre transacción: se ejecuta dentro
 * de la del pago, para que un cobro sin unidades disponibles se revierta completo.
 */
public interface HoldUnitsUseCase {
  void holdUnits(Long reservationId);
}
