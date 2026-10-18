package com.renttool.infrastructure.billing.persistence;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
interface PaymentJpaRepository extends JpaRepository<PaymentEntity,String> {
  List<PaymentEntity> findByReservationIdOrderByCreatedAtDesc(Long reservationId);
}
