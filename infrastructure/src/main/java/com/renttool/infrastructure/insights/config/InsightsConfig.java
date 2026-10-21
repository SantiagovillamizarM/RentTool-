package com.renttool.infrastructure.insights.config;

import com.renttool.application.discovery.port.in.SearchAvailableToolsUseCase;
import com.renttool.application.discovery.service.AvailableToolsService;
import com.renttool.application.inventory.port.in.BrowseCatalogUseCase;
import com.renttool.application.reservation.port.in.CheckAvailabilityUseCase;
import com.renttool.application.insights.port.in.ViewStatisticsUseCase;
import com.renttool.application.insights.service.StatisticsService;
import com.renttool.application.insights.port.in.ProviderPerformanceUseCase;
import com.renttool.application.insights.service.ProviderPerformanceService;
import com.renttool.application.identity.port.out.AccountRepository;
import com.renttool.application.identity.port.out.ProviderProfileRepository;
import com.renttool.application.inventory.port.out.ToolRepository;
import com.renttool.application.reservation.port.out.ReservationRepository;
import com.renttool.application.billing.port.out.PaymentStore;
import com.renttool.application.operations.port.out.RentalRecords;
import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class InsightsConfig {
  @Bean SearchAvailableToolsUseCase availableTools(BrowseCatalogUseCase catalog,
      CheckAvailabilityUseCase availability, Clock clock) {
    return new AvailableToolsService(catalog, availability, clock);
  }
  @Bean ViewStatisticsUseCase statistics(ReservationRepository reservations, ToolRepository tools,
      PaymentStore payments, RentalRecords operations, Clock clock, PlatformTransactionManager manager) {
    var service = new StatisticsService(reservations, tools, payments, operations, clock);
    var transaction = new TransactionTemplate(manager);
    transaction.setReadOnly(true);
    return (actor, range) -> transaction.execute(status -> service.get(actor, range));
  }
  @Bean ProviderPerformanceUseCase providerPerformance(AccountRepository accounts,
      ProviderProfileRepository profiles, ToolRepository tools, ReservationRepository reservations,
      PaymentStore payments, Clock clock, PlatformTransactionManager manager) {
    var service = new ProviderPerformanceService(accounts, profiles, tools, reservations, payments, clock);
    var transaction = new TransactionTemplate(manager);
    transaction.setReadOnly(true);
    return (admin, range) -> transaction.execute(status -> service.get(admin, range));
  }
}
