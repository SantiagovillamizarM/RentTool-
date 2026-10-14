package com.renttool.application.inventory.port.out;

import com.renttool.domain.inventory.ToolImage;
import java.util.List;
import java.util.Optional;

public interface ToolImageRepository {
  Optional<ToolImage> findById(Long id);

  List<ToolImage> findByToolId(Long toolId);

  ToolImage save(ToolImage image);

  void delete(ToolImage image);
}
