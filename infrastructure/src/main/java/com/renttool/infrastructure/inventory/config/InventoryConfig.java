package com.renttool.infrastructure.inventory.config;

import com.renttool.application.inventory.port.out.*;
import com.renttool.application.inventory.service.*;
import org.springframework.context.annotation.*;

/** Ensambla los casos de uso de inventario con sus adaptadores de salida. */
@Configuration
public class InventoryConfig {
  @Bean
  CatalogService catalogService(
      ToolRepository tools,
      CategoryRepository categories,
      UnitRepository units,
      ToolImageRepository images) {
    return new CatalogService(tools, categories, units, images);
  }

  @Bean
  ToolManagementService toolManagementService(
      ToolRepository tools, CategoryRepository categories) {
    return new ToolManagementService(tools, categories);
  }

  @Bean
  UnitManagementService unitManagementService(ToolRepository tools, UnitRepository units) {
    return new UnitManagementService(tools, units);
  }

  @Bean
  ImageManagementService imageManagementService(
      ToolRepository tools, ToolImageRepository images, ImageStorage storage) {
    return new ImageManagementService(tools, images, storage);
  }
}
