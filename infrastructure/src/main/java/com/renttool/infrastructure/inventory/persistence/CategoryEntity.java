package com.renttool.infrastructure.inventory.persistence;

import jakarta.persistence.*;

@Entity
@Table(name = "categories")
class CategoryEntity {
  @Id Long id;
  String name;
  String icon;
}
