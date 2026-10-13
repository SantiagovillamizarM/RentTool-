package com.renttool.inventory.application;

import com.renttool.inventory.domain.Tool;
import java.util.Optional;

public interface ToolRepository {
  Optional<Tool> find(Long id);

  Tool save(Tool tool);
}
