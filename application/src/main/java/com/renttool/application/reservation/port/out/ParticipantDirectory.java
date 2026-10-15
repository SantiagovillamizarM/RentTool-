package com.renttool.application.reservation.port.out;

/** Nombres visibles de las personas que participan en una reserva. */
public interface ParticipantDirectory {
  String nameOf(Long participantId);
}
