package com.renttool.application.reservation.port.out;

import java.time.Instant;
import java.time.LocalDate;

/** Deja constancia de quién descargó qué informe y cuándo: contienen datos personales. */
public interface ReportDownloadLog {
  void record(Long adminId, String report, LocalDate from, LocalDate to, Instant at);
}
