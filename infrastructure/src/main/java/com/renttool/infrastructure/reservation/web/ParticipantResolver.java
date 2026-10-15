package com.renttool.infrastructure.reservation.web;

import com.renttool.domain.reservation.Participant;
import org.springframework.security.oauth2.jwt.Jwt;

/** Traduce el JWT de la petición al participante del dominio de reservas. */
final class ParticipantResolver {
  private ParticipantResolver() {}

  static Participant from(Jwt jwt) {
    if (jwt == null) return Participant.anonymous();
    var role = Participant.Role.valueOf(jwt.getClaimAsString("role"));
    return new Participant(Long.valueOf(jwt.getSubject()), role);
  }
}
