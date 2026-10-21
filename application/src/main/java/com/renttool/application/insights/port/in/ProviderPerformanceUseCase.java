package com.renttool.application.insights.port.in;

import com.renttool.application.reservation.port.in.ReservationReportUseCase.Range;
import com.renttool.domain.reservation.Participant;
import java.math.BigDecimal;
import java.util.List;

/**
 * Comparación de proveedores para el administrador. Venta es una reserva aceptada con pago aprobado
 * (no reembolsado), igual que en el panel del proveedor. Todo se refiere a reservas solicitadas en
 * el rango, en su estado actual.
 */
public interface ProviderPerformanceUseCase {
  Report get(Participant admin, Range range);

  /**
   * @param acceptanceRate aceptadas sobre solicitudes respondidas o vencidas; null sin respuestas
   * @param expiredRate solicitudes que vencieron sin respuesta sobre el total; null sin solicitudes
   * @param alerts motivos para citar al proveedor; vacío si no hay ninguno
   */
  record ProviderRow(Long id, String name, String email, String phone, String city,
      boolean active, long activeTools, long requests, long accepted, long rejected,
      long expired, long cancelled, long pending, long unitsRented, BigDecimal sales,
      BigDecimal averageTicket, Double acceptanceRate, Double expiredRate, List<String> alerts) {}

  /** Promedios de la plataforma, sobre los proveedores listados. */
  record Averages(BigDecimal salesPerProvider, double requestsPerProvider,
      Double acceptanceRate, Double expiredRate) {}

  record Report(Range range, BigDecimal totalSales, long totalRequests, Averages averages,
      long needAttention, List<ProviderRow> providers) {}
}
