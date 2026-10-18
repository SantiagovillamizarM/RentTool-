package com.renttool.application.insights.port.in;

import com.renttool.application.reservation.port.in.ReservationReportUseCase.Range;
import com.renttool.domain.reservation.*;
import java.math.BigDecimal;
import java.util.*;

public interface ViewStatisticsUseCase {
  Summary get(Participant actor, Range range);
  record PopularTool(Long toolId, String name, long reservations, long units) {}
  /** Todo se refiere a reservas solicitadas en el rango, en su estado actual. */
  record Summary(Range range, long reservations, Map<ReservationStatus, Long> byStatus,
      long paidReservations, BigDecimal approvedAmount, long inUse, long returned,
      long overdue, long openIncidents, List<PopularTool> popularTools) {}
}
