package com.renttool.inventory.infrastructure;

import jakarta.persistence.*;

@Entity
@Table(name = "tool_images")
public class ImageEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "tool_id")
  public Long toolId;

  public String location;
}
