package com.renttool.inventory.infrastructure;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "tools")
public class ToolEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "provider_id")
  public Long providerId;

  @Column(name = "category_id")
  public Long categoryId;

  public String name;

  @Column(length = 3000)
  public String description;

  @Column(name = "daily_rate", precision = 14, scale = 2)
  public BigDecimal dailyRate;

  public boolean active;
}
