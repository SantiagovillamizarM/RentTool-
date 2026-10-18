package com.renttool.infrastructure.billing.persistence;
import org.springframework.data.jpa.repository.JpaRepository;
interface InvoiceJpaRepository extends JpaRepository<InvoiceEntity,Long> {}
