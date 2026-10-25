package com.renttool.infrastructure.review.config;

import com.renttool.application.inventory.port.out.ToolRepository;
import com.renttool.application.operations.port.out.RentalRecords;
import com.renttool.application.reservation.port.out.ParticipantDirectory;
import com.renttool.application.reservation.port.out.ReservationRepository;
import com.renttool.application.review.port.in.ReviewToolsUseCase;
import com.renttool.application.review.port.out.ReviewRepository;
import com.renttool.application.review.service.ReviewService;
import com.renttool.domain.reservation.Participant;
import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Ensambla las calificaciones y publica cada caso de uso dentro de su transacción. */
@Configuration
public class ReviewConfig {
  @Bean
  ReviewToolsUseCase reviewUseCases(
      ReviewRepository reviews,
      ReservationRepository reservations,
      RentalRecords records,
      ToolRepository tools,
      ParticipantDirectory directory,
      Clock clock,
      PlatformTransactionManager transactions) {
    var service = new ReviewService(reviews, reservations, records, tools, directory, clock);
    var write = new TransactionTemplate(transactions);
    var read = new TransactionTemplate(transactions);
    read.setReadOnly(true);
    return new ReviewToolsUseCase() {
      public ReviewView rate(Participant client, Long reservationId, RateCommand command) {
        return write.execute(s -> service.rate(client, reservationId, command));
      }
      public ReservationReview ofReservation(Participant participant, Long reservationId) {
        return read.execute(s -> service.ofReservation(participant, reservationId));
      }
      public ToolReviews ofTool(Long toolId) {
        return read.execute(s -> service.ofTool(toolId));
      }
    };
  }
}
