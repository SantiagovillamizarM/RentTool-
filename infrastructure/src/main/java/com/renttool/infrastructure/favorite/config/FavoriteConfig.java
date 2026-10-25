package com.renttool.infrastructure.favorite.config;

import com.renttool.application.favorite.port.in.ManageFavoritesUseCase;
import com.renttool.application.favorite.port.out.FavoriteRepository;
import com.renttool.application.favorite.service.FavoriteService;
import com.renttool.application.inventory.port.in.BrowseCatalogUseCase;
import com.renttool.application.inventory.port.in.ToolDetails;
import com.renttool.domain.reservation.Participant;
import java.time.Clock;
import java.util.List;
import org.springframework.context.annotation.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Ensambla la lista de deseos y publica cada caso de uso dentro de su transacción. */
@Configuration
public class FavoriteConfig {
  @Bean
  ManageFavoritesUseCase favoriteUseCases(FavoriteRepository favorites,
      BrowseCatalogUseCase catalog, Clock clock, PlatformTransactionManager transactions) {
    var service = new FavoriteService(favorites, catalog, clock);
    var write = new TransactionTemplate(transactions);
    var read = new TransactionTemplate(transactions);
    read.setReadOnly(true);
    return new ManageFavoritesUseCase() {
      public List<ToolDetails> list(Participant client) {
        return read.execute(s -> service.list(client));
      }
      public void add(Participant client, Long toolId) {
        write.executeWithoutResult(s -> service.add(client, toolId));
      }
      public void remove(Participant client, Long toolId) {
        write.executeWithoutResult(s -> service.remove(client, toolId));
      }
    };
  }
}
