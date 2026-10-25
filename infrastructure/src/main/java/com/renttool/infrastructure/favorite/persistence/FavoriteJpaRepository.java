package com.renttool.infrastructure.favorite.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface FavoriteJpaRepository extends JpaRepository<FavoriteEntity, FavoriteEntity.Key> {
  List<FavoriteEntity> findByClientIdOrderByCreatedAtDesc(Long clientId);
}
