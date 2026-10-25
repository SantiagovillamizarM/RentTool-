package com.renttool.infrastructure.favorite.persistence;

import com.renttool.application.favorite.port.out.FavoriteRepository;
import com.renttool.domain.favorite.Favorite;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
class FavoritePersistenceAdapter implements FavoriteRepository {
  private final FavoriteJpaRepository jpa;

  FavoritePersistenceAdapter(FavoriteJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public List<Long> toolIdsOf(Long clientId) {
    return jpa.findByClientIdOrderByCreatedAtDesc(clientId).stream().map(e -> e.toolId).toList();
  }

  @Override
  public boolean exists(Long clientId, Long toolId) {
    return jpa.existsById(new FavoriteEntity.Key(clientId, toolId));
  }

  @Override
  public void save(Favorite favorite) {
    var entity = new FavoriteEntity();
    entity.clientId = favorite.clientId();
    entity.toolId = favorite.toolId();
    entity.createdAt = favorite.createdAt();
    jpa.saveAndFlush(entity);
  }

  @Override
  public void delete(Long clientId, Long toolId) {
    jpa.deleteById(new FavoriteEntity.Key(clientId, toolId));
  }
}
