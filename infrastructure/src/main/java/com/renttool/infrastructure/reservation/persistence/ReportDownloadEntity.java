package com.renttool.infrastructure.reservation.persistence;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "report_downloads")
class ReportDownloadEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(name = "admin_id")
  Long adminId;

  String report;

  @Column(name = "range_from")
  LocalDate rangeFrom;

  @Column(name = "range_to")
  LocalDate rangeTo;

  @Column(name = "downloaded_at")
  LocalDateTime downloadedAt;
}
