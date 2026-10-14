package com.renttool.infrastructure.inventory.persistence;

import jakarta.persistence.*;

@Entity
@Table(name = "units")
class UnitEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(name = "tool_id")
  Long toolId;

  String code;
  String status;
}
