package com.renttool.infrastructure.identity.persistence;

import jakarta.persistence.*;

@Entity
@Table(name = "provider_profiles")
public class ProviderProfileEntity {
  @Id
  @Column(name = "account_id")
  public Long accountId;

  @Column(name = "person_type")
  public String personType;

  @Column(unique = true)
  public String nit;

  @Column(name = "verification_digit")
  public int verificationDigit;

  @Column(name = "legal_name")
  public String legalName;

  @Column(name = "trade_name")
  public String tradeName;

  @Column(name = "legal_representative")
  public String legalRepresentative;

  @Column(name = "representative_document")
  public String representativeDocument;

  public String phone;
  public String address;
  public String city;

  @Column(name = "logo_location")
  public String logoLocation;
}
