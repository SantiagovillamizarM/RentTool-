package com.renttool;

import com.renttool.identity.application.AccountRepository;
import com.renttool.identity.domain.Account;
import com.renttool.inventory.application.ToolRepository;
import com.renttool.inventory.domain.Tool;
import com.renttool.inventory.infrastructure.*;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.*;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@Profile("demo")
public class DemoData {
  @Bean
  CommandLineRunner populate(
      AccountRepository accounts,
      ToolRepository tools,
      JpaTools jpa,
      JpaUnits units,
      PasswordEncoder passwords,
      @Value("${DEMO_PASSWORD}") String password) {
    return args -> {
      if (password.length() < 10)
        throw new IllegalArgumentException("DEMO_PASSWORD requiere al menos 10 caracteres");
      var provider =
          accounts
              .findByEmail("proveedor@renttool.demo")
              .orElseGet(
                  () ->
                      accounts.save(
                          new Account(
                              null,
                              "Equipo RentTool Demo",
                              "proveedor@renttool.demo",
                              passwords.encode(password),
                              Account.Role.PROVEEDOR,
                              true)));
      if (jpa.count() > 0) return;
      String[] names = {
        "Taladro percutor 20V",
        "Rotomartillo SDS Plus",
        "Nivel láser autonivelante",
        "Amoladora angular 4½″",
        "Mezcladora de concreto",
        "Escalera telescópica"
      };
      String[] descriptions = {
        "Taladro de batería para perforación y atornillado. Publicación de demostración.",
        "Equipo de percusión para trabajos en concreto y mampostería. Publicación de demostración.",
        "Líneas de referencia para alineación y nivelación de interiores. Publicación de"
            + " demostración.",
        "Herramienta compacta para corte y desbaste con accesorios adecuados. Publicación de"
            + " demostración.",
        "Equipo para preparación de mezclas en proyectos de construcción. Publicación de"
            + " demostración.",
        "Acceso a zonas elevadas con unidades identificadas y revisadas. Publicación de"
            + " demostración."
      };
      long[] categories = {1, 2, 3, 1, 2, 4};
      int[] rates = {25000, 45000, 35000, 22000, 85000, 30000};
      for (int i = 0; i < names.length; i++) {
        var t =
            tools.save(
                new Tool(
                    null,
                    provider.id(),
                    categories[i],
                    names[i],
                    descriptions[i],
                    BigDecimal.valueOf(rates[i]),
                    true));
        for (int n = 1; n <= 2; n++) {
          var u = new UnitEntity();
          u.toolId = t.id();
          u.code = "DEMO-" + t.id() + "-" + n;
          u.status = "OPERATIVA";
          units.save(u);
        }
      }
    };
  }
}
