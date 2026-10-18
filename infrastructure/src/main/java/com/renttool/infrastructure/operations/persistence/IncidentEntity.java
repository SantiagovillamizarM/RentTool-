package com.renttool.infrastructure.operations.persistence;
import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name = "incidents")
class IncidentEntity {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
  Long reservationId;
  Long unitId;
  @Column(name = "incident_type", length = 20) String type;
  @Column(length = 1000) String description;
  Long reportedBy;
  Instant reportedAt;
  Long resolvedBy;
  Instant resolvedAt;
  @Column(length = 1000) String resolution;
}
