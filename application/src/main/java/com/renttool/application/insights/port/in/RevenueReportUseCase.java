package com.renttool.application.insights.port.in;

import com.renttool.application.reservation.port.in.ReservationReportUseCase.Range;
import com.renttool.domain.reservation.Participant;
import java.math.BigDecimal;
import java.util.List;

/**
 * Ingresos y rentabilidad de las reservas solicitadas en el rango, agrupados por herramienta o por
 * mes de solicitud. El proveedor ve solo sus herramientas; el administrador, toda la plataforma.
 */
public interface RevenueReportUseCase {
  Report get(Participant actor, Range range, GroupBy groupBy);

  enum GroupBy { TOOL, MONTH }

  /**
   * @param key id de la herramienta o mes en formato 2026-10
   * @param unitDays días alquilados por unidades: 3 días con 2 unidades son 6
   * @param rentalIncome total de las reservas vendidas, con sus extensiones
   * @param extraCharges recargos por retraso y cobros por daño o pérdida
   * @param incomePerUnitDay rentabilidad: ingreso por cada día-unidad alquilado
   * @param share porcentaje del ingreso total del reporte
   */
  record Row(String key, String label, long reservations, long unitDays, BigDecimal rentalIncome,
      BigDecimal extraCharges, BigDecimal income, BigDecimal averagePerReservation,
      BigDecimal incomePerUnitDay, double share) {}

  /** Solo cuentan las ventas: reservas aceptadas con pago aprobado. */
  record Report(Range range, GroupBy groupBy, long reservations, BigDecimal rentalIncome,
      BigDecimal extraCharges, BigDecimal income, List<Row> rows) {}
}
