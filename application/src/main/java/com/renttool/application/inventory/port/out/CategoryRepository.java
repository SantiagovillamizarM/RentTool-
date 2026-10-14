package com.renttool.application.inventory.port.out;

import com.renttool.domain.inventory.Category;
import java.util.List;
import java.util.Optional;

public interface CategoryRepository {
  List<Category> findAll();

  Optional<Category> findById(Long id);

  boolean existsById(Long id);
}
