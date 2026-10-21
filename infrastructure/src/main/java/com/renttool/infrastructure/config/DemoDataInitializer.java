package com.renttool.infrastructure.config;

import com.renttool.application.identity.port.in.ManageProvidersUseCase.ProfileCommand;
import com.renttool.application.identity.port.in.ProvisionAccountUseCase;
import com.renttool.application.inventory.port.in.*;
import com.renttool.application.shared.PageQuery;
import com.renttool.domain.identity.PersonType;
import com.renttool.domain.inventory.Actor;
import com.renttool.domain.inventory.UnitStatus;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;

/**
 * Perfil demo: el proveedor Midnight publica su catálogo como lo haría desde su panel, a través de
 * los mismos casos de uso (publicar, imagen principal, unidades). Nada se escribe directo en la
 * base. Es idempotente: si Midnight ya tiene herramientas, no vuelve a publicarlas.
 *
 * <p>La cuenta demo anterior ("Equipo RentTool Demo") se da de baja y sus herramientas se retiran
 * del catálogo; su historial se conserva.
 */
@Configuration
@Profile("demo")
public class DemoDataInitializer {
  private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);
  static final String MIDNIGHT_EMAIL = "midnight@renttool.demo";
  static final String LEGACY_EMAIL = "proveedor@renttool.demo";
  private static final long ELECTRICAS = 1, ILUMINACION = 2, MANUALES = 3;

  private static final ProfileCommand MIDNIGHT =
      new ProfileCommand(
          PersonType.JURIDICA,
          "901478236",
          1,
          "Midnight Herramientas S.A.S.",
          "Midnight",
          "Santiago Villamizar Mantilla",
          "CC 1098765432",
          "+57 607 645 2210",
          "Carrera 27 # 45-12, Cabecera del Llano",
          "Bucaramanga");

  /** Una herramienta del catálogo de Midnight: su imagen está en demo/tools/{image}. */
  private record DemoTool(
      String code, String name, long categoryId, int dailyRate, int units, String image,
      String description) {}

  private static final DemoTool[] TOOLS = {
    // Eléctricas
    new DemoTool("TAL", "Taladro percutor eléctrico", ELECTRICAS, 25000, 3, "taladro-percutor.png",
        "Taladro percutor con cable para perforar madera, metal y mampostería. Mandril de 13 mm y"
            + " velocidad variable."),
    new DemoTool("ROT", "Rotomartillo SDS Plus", ELECTRICAS, 45000, 2, "rotomartillo-sds-plus.png",
        "Tres modos: perforar, percutir y cincelar, hasta 4.850 golpes por minuto. Incluye maletín,"
            + " brocas SDS y cinceles."),
    new DemoTool("RPV", "Rompepavimentos eléctrico", ELECTRICAS, 120000, 2, "rompepavimentos.png",
        "Martillo demoledor de alta potencia con sistema antivibración para romper concreto,"
            + " asfalto y pisos."),
    new DemoTool("PUL", "Pulidora angular 4½″", ELECTRICAS, 22000, 3, "pulidora-angular.png",
        "Pulidora de 115 mm para cortar y desbastar metal, cerámica y concreto con el disco"
            + " adecuado."),
    new DemoTool("SCI", "Sierra circular 7¼″", ELECTRICAS, 35000, 2, "sierra-circular.png",
        "Cortes rectos en madera y tableros, con ajuste de profundidad y bisel."),
    new DemoTool("CAL", "Caladora", ELECTRICAS, 25000, 2, "caladora.png",
        "Sierra caladora para cortes curvos y rectos en madera, metal y PVC, con velocidad"
            + " variable."),
    new DemoTool("PCA", "Pistola de calor", ELECTRICAS, 18000, 2, "pistola-de-calor.png",
        "Decapa pintura, termoencoge, moldea PVC y seca superficies; temperatura regulable."),
    new DemoTool("CAU", "Cautín para soldar", ELECTRICAS, 12000, 3, "cautin.png",
        "Soldadura con estaño para trabajos eléctricos y electrónicos, con base de soporte."),
    new DemoTool("MEZ", "Mezcladora de concreto", ELECTRICAS, 85000, 2,
        "mezcladora-de-concreto.png",
        "Mezcladora eléctrica de tambor para preparar concreto y mortero en obra."),
    new DemoTool("NIV", "Nivel láser autonivelante", ELECTRICAS, 35000, 2, "nivel-laser.png",
        "Proyecta líneas horizontales y verticales para alinear y nivelar en interiores."),
    new DemoTool("PAM", "Pinza amperimétrica", ELECTRICAS, 20000, 2, "pinza-amperimetrica.png",
        "Mide corriente sin abrir el circuito, además de voltaje, resistencia y continuidad."),
    new DemoTool("VOL", "Voltímetro digital", ELECTRICAS, 15000, 3, "voltimetro.png",
        "Mide voltaje, corriente y continuidad en instalaciones eléctricas."),
    // Iluminación
    new DemoTool("LED", "Set de proyectores LED con trípode", ILUMINACION, 40000, 2,
        "proyectores-led.png",
        "Torres de iluminación LED con trípode telescópico y batería para trabajo nocturno en"
            + " obra."),
    // Manuales
    new DemoTool("DES", "Set de destornilladores", MANUALES, 8000, 4, "set-destornilladores.png",
        "Juego de destornilladores de pala, estrella y precisión con mangos ergonómicos."),
    new DemoTool("MAR", "Set de martillos", MANUALES, 10000, 3, "set-martillos.png",
        "Martillos de uña, de bola y mazo de goma para carpintería y obra."),
    new DemoTool("REM", "Remachadora manual", MANUALES, 9000, 3, "remachadora.png",
        "Remachadora de palanca para remaches pop de 2,4 a 4,8 mm en metal y aluminio."),
    new DemoTool("CUR", "Curvadora de tubos hidráulica", MANUALES, 60000, 1,
        "curvadora-de-tubos.png",
        "Curva tubos de acero y conduit con bomba hidráulica manual y hormas intercambiables."),
    new DemoTool("ESC", "Escalera telescópica", MANUALES, 30000, 2, "escalera-telescopica.png",
        "Escalera de aluminio extensible para acceder con seguridad a zonas elevadas."),
  };

  @Bean
  CommandLineRunner populate(
      ProvisionAccountUseCase accounts,
      BrowseCatalogUseCase catalog,
      ManageToolsUseCase tools,
      ManageUnitsUseCase units,
      ManageImagesUseCase images,
      @Value("${DEMO_PASSWORD}") String password) {
    return args -> {
      if (password.length() < 10)
        throw new IllegalArgumentException("DEMO_PASSWORD requiere al menos 10 caracteres");
      var midnight = accounts.ensureProvider(MIDNIGHT_EMAIL, password, MIDNIGHT);
      var actor = new Actor(midnight.id(), true, false);
      if (activeToolsOf(catalog, actor) == 0) {
        for (var demo : TOOLS) publish(demo, actor, tools, units, images);
        log.info("Midnight publicó {} herramientas en el catálogo", TOOLS.length);
      }
      retireLegacyDemo(accounts, catalog, tools);
    };
  }

  private static void publish(
      DemoTool demo,
      Actor actor,
      ManageToolsUseCase tools,
      ManageUnitsUseCase units,
      ManageImagesUseCase images) {
    var tool =
        tools.publish(
            actor,
            new ManageToolsUseCase.Command(
                demo.categoryId(),
                demo.name(),
                demo.description(),
                BigDecimal.valueOf(demo.dailyRate())));
    images.replaceMain(actor, tool.id(), image(demo.image()));
    for (int n = 1; n <= demo.units(); n++)
      units.add(
          actor,
          tool.id(),
          new ManageUnitsUseCase.Command(
              "MID-%s-%02d".formatted(demo.code(), n), UnitStatus.OPERATIVA));
  }

  /** La cuenta demo anterior sale del catálogo: sus herramientas se retiran y ella se da de baja. */
  private static void retireLegacyDemo(
      ProvisionAccountUseCase accounts, BrowseCatalogUseCase catalog, ManageToolsUseCase tools) {
    var legacy = accounts.findAccount(LEGACY_EMAIL).filter(a -> !a.isRemoved());
    if (legacy.isEmpty()) return;
    var actor = new Actor(legacy.get().id(), true, false);
    var page = catalog.search(mine(actor, 100));
    page.content().forEach(t -> tools.retire(actor, t.tool().id()));
    accounts.removeAccount(LEGACY_EMAIL);
    log.info("Cuenta demo anterior dada de baja; {} herramientas retiradas", page.content().size());
  }

  private static long activeToolsOf(BrowseCatalogUseCase catalog, Actor actor) {
    return catalog.search(mine(actor, 1)).totalElements();
  }

  private static BrowseCatalogUseCase.Query mine(Actor actor, int size) {
    return new BrowseCatalogUseCase.Query(
        "", null, null, true, actor, new PageQuery(0, size), CatalogOrder.RECENT);
  }

  private static byte[] image(String file) {
    try (var in = new ClassPathResource("demo/tools/" + file).getInputStream()) {
      return in.readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException("No se encontró la imagen demo " + file, e);
    }
  }
}
