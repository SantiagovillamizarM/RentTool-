package com.renttool.infrastructure.inventory.persistence;

import jakarta.persistence.*;

@Entity
@Table(name = "tool_images")
class ToolImageEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(name = "tool_id")
  Long toolId;

  String location;

  int position;
}
