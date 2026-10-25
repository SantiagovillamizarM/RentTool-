package com.renttool.infrastructure.favorite.persistence;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "favorites")
@IdClass(FavoriteEntity.Key.class)
class FavoriteEntity {
  @Id Long clientId;
  @Id Long toolId;
  Instant createdAt;

  public static class Key implements Serializable {
    public Long clientId;
    public Long toolId;

    public Key() {}

    public Key(Long clientId, Long toolId) {
      this.clientId = clientId;
      this.toolId = toolId;
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof Key key
          && Objects.equals(clientId, key.clientId)
          && Objects.equals(toolId, key.toolId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(clientId, toolId);
    }
  }
}
