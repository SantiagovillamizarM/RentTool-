package com.renttool.domain.favorite;

import com.renttool.domain.shared.exception.InvalidInputException;
import java.time.Instant;

/** Herramienta que un cliente guarda en su lista de deseos para reservarla después. */
public record Favorite(Long clientId, Long toolId, Instant createdAt) {
  public Favorite {
    if (clientId == null || toolId == null || createdAt == null)
      throw new InvalidInputException("El favorito requiere cliente y herramienta");
  }
}
