package com.renttool.inventory.infrastructure;

import jakarta.persistence.*;

@Entity
@Table(name = "units")
public class UnitEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "tool_id")
  public Long toolId;

  public String code;
  public String status;
}
