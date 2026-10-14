package com.renttool.infrastructure.inventory.persistence;

import com.renttool.application.inventory.port.out.CategoryRepository;
import com.renttool.domain.inventory.Category;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class CategoryPersistenceAdapter implements CategoryRepository {
  private final CategoryJpaRepository jpa;

  CategoryPersistenceAdapter(CategoryJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public List<Category> findAll() {
    return jpa.findAll().stream().map(CategoryPersistenceAdapter::toDomain).toList();
  }

  @Override
  public Optional<Category> findById(Long id) {
    return jpa.findById(id).map(CategoryPersistenceAdapter::toDomain);
  }

  @Override
  public boolean existsById(Long id) {
    return jpa.existsById(id);
  }

  private static Category toDomain(CategoryEntity e) {
    return new Category(e.id, e.name, e.icon);
  }
}
