package com.renttool.application.favorite.service;

import com.renttool.application.favorite.port.in.ManageFavoritesUseCase;
import com.renttool.application.favorite.port.out.FavoriteRepository;
import com.renttool.application.inventory.port.in.BrowseCatalogUseCase;
import com.renttool.application.inventory.port.in.ToolDetails;
import com.renttool.domain.favorite.Favorite;
import com.renttool.domain.reservation.Participant;
import com.renttool.domain.shared.exception.ForbiddenOperationException;
import com.renttool.domain.shared.exception.NotFoundException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/** Solo un cliente guarda favoritos, y solo de herramientas publicadas. */
public class FavoriteService implements ManageFavoritesUseCase {
  private final FavoriteRepository favorites;
  private final BrowseCatalogUseCase catalog;
  private final Clock clock;

  public FavoriteService(FavoriteRepository favorites, BrowseCatalogUseCase catalog, Clock clock) {
    this.favorites = favorites;
    this.catalog = catalog;
    this.clock = clock;
  }

  @Override
  public List<ToolDetails> list(Participant client) {
    ensureClient(client);
    var result = new ArrayList<ToolDetails>();
    for (Long toolId : favorites.toolIdsOf(client.id())) {
      try {
        result.add(catalog.findActive(toolId));
      } catch (NotFoundException retired) {
        // Se conserva guardada, pero no se muestra mientras no esté publicada.
      }
    }
    return result;
  }

  @Override
  public void add(Participant client, Long toolId) {
    ensureClient(client);
    catalog.findActive(toolId);
    if (!favorites.exists(client.id(), toolId))
      favorites.save(new Favorite(client.id(), toolId, clock.instant()));
  }

  @Override
  public void remove(Participant client, Long toolId) {
    ensureClient(client);
    favorites.delete(client.id(), toolId);
  }

  private static void ensureClient(Participant participant) {
    if (!participant.isClient())
      throw new ForbiddenOperationException("Solo los clientes guardan favoritos");
  }
}
