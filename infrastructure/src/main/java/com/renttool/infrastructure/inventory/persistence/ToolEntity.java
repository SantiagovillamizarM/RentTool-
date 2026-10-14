package com.renttool.infrastructure.inventory.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "tools")
class ToolEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(name = "provider_id")
  Long providerId;

  @Column(name = "category_id")
  Long categoryId;

  String name;

  @Column(length = 3000)
  String description;

  @Column(name = "daily_rate", precision = 14, scale = 2)
  BigDecimal dailyRate;

  boolean active;
}
