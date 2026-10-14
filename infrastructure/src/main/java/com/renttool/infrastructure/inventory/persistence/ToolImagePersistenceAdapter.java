package com.renttool.infrastructure.inventory.persistence;

import com.renttool.application.inventory.port.out.ToolImageRepository;
import com.renttool.domain.inventory.ToolImage;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class ToolImagePersistenceAdapter implements ToolImageRepository {
  private final ToolImageJpaRepository jpa;

  ToolImagePersistenceAdapter(ToolImageJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public Optional<ToolImage> findById(Long id) {
    return jpa.findById(id).map(ToolImagePersistenceAdapter::toDomain);
  }

  @Override
  public List<ToolImage> findByToolId(Long toolId) {
    return jpa.findByToolId(toolId).stream().map(ToolImagePersistenceAdapter::toDomain).toList();
  }

  @Override
  public ToolImage save(ToolImage image) {
    var e = new ToolImageEntity();
    e.id = image.id();
    e.toolId = image.toolId();
    e.location = image.location();
    return toDomain(jpa.save(e));
  }

  @Override
  public void delete(ToolImage image) {
    jpa.deleteById(image.id());
  }

  private static ToolImage toDomain(ToolImageEntity e) {
    return new ToolImage(e.id, e.toolId, e.location);
  }
}
