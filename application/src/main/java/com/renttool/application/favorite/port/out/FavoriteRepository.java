package com.renttool.application.favorite.port.out;

import com.renttool.domain.favorite.Favorite;
import java.util.List;

public interface FavoriteRepository {
  /** Herramientas guardadas por el cliente, la más reciente primero. */
  List<Long> toolIdsOf(Long clientId);

  boolean exists(Long clientId, Long toolId);

  void save(Favorite favorite);

  void delete(Long clientId, Long toolId);
}
