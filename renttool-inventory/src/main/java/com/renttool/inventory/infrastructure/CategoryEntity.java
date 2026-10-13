package com.renttool.inventory.infrastructure;

import jakarta.persistence.*;

@Entity
@Table(name = "categories")
public class CategoryEntity {
  @Id public Long id;
  public String name;
  public String icon;
}
