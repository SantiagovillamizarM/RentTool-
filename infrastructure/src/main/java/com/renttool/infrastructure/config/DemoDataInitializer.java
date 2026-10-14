package com.renttool.infrastructure.config;

import com.renttool.application.identity.port.in.ProvisionAccountUseCase;
import com.renttool.domain.identity.Role;
import com.renttool.application.inventory.port.in.*;
import com.renttool.domain.inventory.Actor;
import com.renttool.domain.inventory.UnitStatus;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.*;

/** Perfil demo: publica seis herramientas de ejemplo si el inventario está vacío. */
@Configuration
@Profile("demo")
public class DemoDataInitializer {
  private record DemoTool(String name, String description, long categoryId, int dailyRate) {}

  private static final DemoTool[] TOOLS = {
    new DemoTool(
        "Taladro percutor 20V",
        "Taladro de batería para perforación y atornillado. Publicación de demostración.",
        1,
        25000),
    new DemoTool(
        "Rotomartillo SDS Plus",
        "Equipo de percusión para trabajos en concreto y mampostería. Publicación de"
            + " demostración.",
        2,
        45000),
    new DemoTool(
        "Nivel láser autonivelante",
        "Líneas de referencia para alineación y nivelación de interiores. Publicación de"
            + " demostración.",
        3,
        35000),
    new DemoTool(
        "Amoladora angular 4½″",
        "Herramienta compacta para corte y desbaste con accesorios adecuados. Publicación de"
            + " demostración.",
        1,
        22000),
    new DemoTool(
        "Mezcladora de concreto",
        "Equipo para preparación de mezclas en proyectos de construcción. Publicación de"
            + " demostración.",
        2,
        85000),
    new DemoTool(
        "Escalera telescópica",
        "Acceso a zonas elevadas con unidades identificadas y revisadas. Publicación de"
            + " demostración.",
        4,
        30000)
  };

  @Bean
  CommandLineRunner populate(
      ProvisionAccountUseCase accounts,
      BrowseCatalogUseCase catalog,
      ManageToolsUseCase tools,
      ManageUnitsUseCase units,
      @Value("${DEMO_PASSWORD}") String password) {
    return args -> {
      if (password.length() < 10)
        throw new IllegalArgumentException("DEMO_PASSWORD requiere al menos 10 caracteres");
      var provider =
          accounts.ensureAccount(
              "Equipo RentTool Demo", "proveedor@renttool.demo", password, Role.PROVEEDOR);
      if (!catalog.isInventoryEmpty()) return;
      var actor = new Actor(provider.id(), true, false);
      for (var demo : TOOLS) {
        var tool =
            tools.publish(
                actor,
                new ManageToolsUseCase.Command(
                    demo.categoryId(),
                    demo.name(),
                    demo.description(),
                    BigDecimal.valueOf(demo.dailyRate())));
        for (int n = 1; n <= 2; n++)
          units.add(
              actor,
              tool.id(),
              new ManageUnitsUseCase.Command(
                  "DEMO-" + tool.id() + "-" + n, UnitStatus.OPERATIVA));
      }
    };
  }
}
