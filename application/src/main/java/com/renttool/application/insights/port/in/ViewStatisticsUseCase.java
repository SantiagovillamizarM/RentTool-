package com.renttool.application.insights.port.in;

import com.renttool.application.reservation.port.in.ReservationReportUseCase.Range;
import com.renttool.domain.reservation.*;
import java.math.BigDecimal;
import java.util.*;

public interface ViewStatisticsUseCase {
  Summary get(Participant actor, Range range);
  record PopularTool(Long toolId, String name, long reservations, long units) {}
  /**
   * Todo se refiere a reservas solicitadas en el rango, en su estado actual.
   *
   * @param sales reservas aceptadas con pago aprobado (la misma definición que usa el administrador)
   * @param paidAwaitingAcceptance pagos de solicitudes que el proveedor todavía no acepta
   * @param inUse entregas registradas que aún no se devuelven
   */
  record Summary(Range range, long reservations, Map<ReservationStatus, Long> byStatus,
      BigDecimal sales, BigDecimal paidAwaitingAcceptance, long inUse, long openIncidents,
      List<PopularTool> popularTools) {}
}
