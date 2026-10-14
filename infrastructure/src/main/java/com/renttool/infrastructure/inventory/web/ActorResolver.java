package com.renttool.infrastructure.inventory.web;

import com.renttool.domain.inventory.Actor;
import org.springframework.security.oauth2.jwt.Jwt;

/** Traduce el JWT de la petición al actor del dominio de inventario. */
final class ActorResolver {
  private ActorResolver() {}

  static Actor from(Jwt jwt) {
    if (jwt == null) return Actor.anonymous();
    String role = jwt.getClaimAsString("role");
    return new Actor(
        Long.valueOf(jwt.getSubject()), "PROVEEDOR".equals(role), "ADMINISTRADOR".equals(role));
  }
}
