package com.renttool.infrastructure.identity.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Solicitud para ser proveedor; los instantes se guardan en UTC. */
@Entity
@Table(name = "provider_applications")
class ProviderApplicationEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(name = "account_id")
  Long accountId;

  String status;

  @Column(name = "person_type")
  String personType;

  String nit;

  @Column(name = "verification_digit")
  int verificationDigit;

  @Column(name = "legal_name")
  String legalName;

  @Column(name = "trade_name")
  String tradeName;

  @Column(name = "legal_representative")
  String legalRepresentative;

  @Column(name = "representative_document")
  String representativeDocument;

  String phone;
  String address;
  String city;

  @Column(name = "submitted_at")
  LocalDateTime submittedAt;

  @Column(name = "decided_by")
  Long decidedBy;

  @Column(name = "decided_at")
  LocalDateTime decidedAt;

  @Column(name = "rejection_reason", length = 500)
  String rejectionReason;

  @Column(name = "notified_at")
  LocalDateTime notifiedAt;
}
