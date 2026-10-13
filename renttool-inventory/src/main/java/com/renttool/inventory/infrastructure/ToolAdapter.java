package com.renttool.inventory.infrastructure;

import com.renttool.inventory.application.ToolRepository;
import com.renttool.inventory.domain.Tool;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class ToolAdapter implements ToolRepository {
  private final JpaTools jpa;

  public ToolAdapter(JpaTools jpa) {
    this.jpa = jpa;
  }

  public static Tool map(ToolEntity e) {
    return new Tool(e.id, e.providerId, e.categoryId, e.name, e.description, e.dailyRate, e.active);
  }

  public Optional<Tool> find(Long id) {
    return jpa.findById(id).map(ToolAdapter::map);
  }

  public Tool save(Tool t) {
    var e = new ToolEntity();
    e.id = t.id();
    e.providerId = t.providerId();
    e.categoryId = t.categoryId();
    e.name = t.name();
    e.description = t.description();
    e.dailyRate = t.dailyRate();
    e.active = t.active();
    return map(jpa.save(e));
  }
}
