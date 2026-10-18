package com.renttool.application.discovery.service;

import com.renttool.application.discovery.port.in.SearchAvailableToolsUseCase;
import com.renttool.application.inventory.port.in.*;
import com.renttool.application.reservation.port.in.CheckAvailabilityUseCase;
import com.renttool.application.shared.*;
import com.renttool.domain.reservation.*;
import com.renttool.domain.shared.exception.*;
import java.time.*;
import java.util.ArrayList;

/** Filtra antes de paginar: una página vacía nunca oculta coincidencias posteriores. */
public class AvailableToolsService implements SearchAvailableToolsUseCase {
  private final BrowseCatalogUseCase catalog;
  private final CheckAvailabilityUseCase availability;
  private final Clock clock;
  public AvailableToolsService(BrowseCatalogUseCase catalog, CheckAvailabilityUseCase availability, Clock clock) {
    this.catalog = catalog; this.availability = availability; this.clock = clock;
  }
  public PageResult<ToolDetails> search(BrowseCatalogUseCase.Query query, LocalDate start, LocalDate end, int quantity) {
    if (quantity < 1 || quantity > Reservation.MAX_QUANTITY)
      throw new InvalidInputException("La cantidad debe estar entre 1 y " + Reservation.MAX_QUANTITY);
    if (start == null && end == null) {
      if (quantity != 1) throw new InvalidInputException("Indica las fechas para buscar por cantidad disponible");
      return catalog.search(query);
    }
    var period = new RentalPeriod(start, end);
    if (period.startsBefore(LocalDate.now(clock)))
      throw new InvalidInputException("No se puede buscar disponibilidad en fechas pasadas");
    var matches = new ArrayList<ToolDetails>();
    int page = 0;
    PageResult<ToolDetails> batch;
    do {
      batch = catalog.search(new BrowseCatalogUseCase.Query(query.text(), query.categoryId(),
          query.providerId(), query.onlyMine(), query.actor(), new PageQuery(page++, 100), query.order()));
      for (var tool : batch.content()) {
        try {
          if (availability.check(tool.tool().id(), period, quantity).enough()) matches.add(tool);
        } catch (NotFoundException ignored) {
          // Puede haberse retirado entre la consulta del catálogo y la de disponibilidad.
        }
      }
    } while (page < batch.totalPages());
    long offset = (long) query.page().page() * query.page().size();
    int from = (int) Math.min(offset, matches.size());
    int to = (int) Math.min(offset + query.page().size(), matches.size());
    return new PageResult<>(matches.subList(from, to), matches.size(),
        (int) ((matches.size() + (long) query.page().size() - 1) / query.page().size()), query.page().page());
  }
}
