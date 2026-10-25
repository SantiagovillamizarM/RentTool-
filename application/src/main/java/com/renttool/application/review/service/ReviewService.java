package com.renttool.application.review.service;

import com.renttool.application.inventory.port.out.ToolRepository;
import com.renttool.application.operations.port.out.RentalRecords;
import com.renttool.application.reservation.port.out.ParticipantDirectory;
import com.renttool.application.reservation.port.out.ReservationRepository;
import com.renttool.application.review.port.in.ReviewToolsUseCase;
import com.renttool.application.review.port.out.ReviewRepository;
import com.renttool.domain.inventory.Tool;
import com.renttool.domain.reservation.Participant;
import com.renttool.domain.reservation.Reservation;
import com.renttool.domain.review.Review;
import com.renttool.domain.shared.exception.*;
import java.time.Clock;

/**
 * Solo califica quien alquiló y ya devolvió el equipo, para que cada opinión venga de un uso real.
 * No abre transacciones: el adaptador que lo publica las aporta.
 */
public class ReviewService implements ReviewToolsUseCase {
  private final ReviewRepository reviews;
  private final ReservationRepository reservations;
  private final RentalRecords records;
  private final ToolRepository tools;
  private final ParticipantDirectory directory;
  private final Clock clock;

  public ReviewService(
      ReviewRepository reviews,
      ReservationRepository reservations,
      RentalRecords records,
      ToolRepository tools,
      ParticipantDirectory directory,
      Clock clock) {
    this.reviews = reviews;
    this.reservations = reservations;
    this.records = records;
    this.tools = tools;
    this.directory = directory;
    this.clock = clock;
  }

  @Override
  public ReviewView rate(Participant client, Long reservationId, RateCommand command) {
    var reservation = reservation(reservationId);
    if (!isClientOf(client, reservation))
      throw new ForbiddenOperationException("Solo el cliente de la reserva puede calificarla");
    if (!returned(reservation))
      throw new ConflictException("Podrás calificar cuando se registre la devolución del equipo");
    if (reviews.findByReservationId(reservationId).isPresent())
      throw new ConflictException("Ya calificaste esta reserva");
    if (command == null) throw new InvalidInputException("Indica la calificación");
    var saved =
        reviews.save(
            Review.create(
                reservationId,
                reservation.toolId(),
                client.id(),
                command.rating(),
                command.comment(),
                clock.instant()));
    return view(saved);
  }

  @Override
  public ReservationReview ofReservation(Participant participant, Long reservationId) {
    var reservation = reservation(reservationId);
    reservation.ensureVisibleTo(participant, tool(reservation.toolId()).providerId());
    var review = reviews.findByReservationId(reservationId).map(this::view).orElse(null);
    boolean canReview = review == null && isClientOf(participant, reservation) && returned(reservation);
    return new ReservationReview(canReview, review);
  }

  @Override
  public ToolReviews ofTool(Long toolId) {
    tool(toolId);
    var list = reviews.findByToolId(toolId).stream().map(this::view).toList();
    double average =
        list.isEmpty()
            ? 0
            : Math.round(list.stream().mapToInt(ReviewView::rating).average().orElse(0) * 10)
                / 10.0;
    return new ToolReviews(average, list.size(), list);
  }

  private static boolean isClientOf(Participant participant, Reservation reservation) {
    return participant.isClient() && reservation.clientId().equals(participant.id());
  }

  private boolean returned(Reservation reservation) {
    return records.operation(reservation.id()).map(o -> !o.isOut()).orElse(false);
  }

  private ReviewView view(Review r) {
    return new ReviewView(
        r.id(),
        r.reservationId(),
        r.toolId(),
        directory.nameOf(r.clientId()),
        r.rating(),
        r.comment(),
        r.createdAt());
  }

  private Reservation reservation(Long id) {
    return reservations
        .findById(id)
        .orElseThrow(() -> new NotFoundException("Reserva no encontrada"));
  }

  private Tool tool(Long id) {
    return tools.findById(id).orElseThrow(() -> new NotFoundException("Herramienta no encontrada"));
  }
}
