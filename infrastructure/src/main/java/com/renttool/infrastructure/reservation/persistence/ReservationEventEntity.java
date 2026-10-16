package com.renttool.infrastructure.reservation.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Fila del historial; occurred_at se guarda en UTC como el resto de instantes. */
@Entity
@Table(name = "reservation_events")
class ReservationEventEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(name = "reservation_id")
  Long reservationId;

  String action;

  @Column(name = "actor_id")
  Long actorId;

  @Column(name = "actor_role")
  String actorRole;

  @Column(name = "occurred_at")
  LocalDateTime occurredAt;

  @Column(length = 500)
  String detail;
}
