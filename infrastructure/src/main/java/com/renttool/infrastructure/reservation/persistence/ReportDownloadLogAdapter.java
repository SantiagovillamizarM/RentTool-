package com.renttool.infrastructure.reservation.persistence;

import com.renttool.application.reservation.port.out.ReportDownloadLog;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Repository;

@Repository
class ReportDownloadLogAdapter implements ReportDownloadLog {
  private final ReportDownloadJpaRepository jpa;

  ReportDownloadLogAdapter(ReportDownloadJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public void record(Long adminId, String report, LocalDate from, LocalDate to, Instant at) {
    var e = new ReportDownloadEntity();
    e.adminId = adminId;
    e.report = report;
    e.rangeFrom = from;
    e.rangeTo = to;
    e.downloadedAt = LocalDateTime.ofInstant(at, ZoneOffset.UTC);
    jpa.save(e);
  }
}
