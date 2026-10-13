package com.renttool.inventory.infrastructure;

public interface JpaImages
    extends org.springframework.data.jpa.repository.JpaRepository<ImageEntity, Long> {
  java.util.List<ImageEntity> findByToolId(Long toolId);
}
