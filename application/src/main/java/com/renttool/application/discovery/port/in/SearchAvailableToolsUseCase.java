package com.renttool.application.discovery.port.in;

import com.renttool.application.inventory.port.in.*;
import com.renttool.application.shared.PageResult;
import java.time.LocalDate;

public interface SearchAvailableToolsUseCase {
  PageResult<ToolDetails> search(BrowseCatalogUseCase.Query query, LocalDate start, LocalDate end, int quantity);
}
