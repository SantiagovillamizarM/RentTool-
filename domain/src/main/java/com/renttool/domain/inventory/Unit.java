package com.renttool.domain.inventory;

import com.renttool.domain.shared.exception.InvalidInputException;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

/** Ejemplar físico de una herramienta, identificado por un código único dentro de ella. */
public record Unit(Long id, Long toolId, String code, UnitStatus status) {
  public Unit {
    if (toolId == null) throw new InvalidInputException("La unidad requiere una herramienta");
    if (code == null || code.isBlank()) throw new InvalidInputException("El código es obligatorio");
    if (status == null) throw new InvalidInputException("El estado es obligatorio");
  }

  public static Unit register(Long toolId, String code, UnitStatus status) {
    return new Unit(null, toolId, code, status);
  }

  public Unit change(String newCode, UnitStatus newStatus) {
    return new Unit(id, toolId, newCode, newStatus);
  }

  public Unit retire() {
    return new Unit(id, toolId, code, UnitStatus.RETIRADA);
  }

  /**
   * Siguiente código libre para una herramienta, con un prefijo tomado de su nombre: "Taladro
   * percutor" da TAL-01, TAL-02... Se salta los códigos ya usados, retirados incluidos.
   */
  public static String nextCode(String toolName, Set<String> taken) {
    String letters =
        Normalizer.normalize(toolName, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .replaceAll("[^A-Za-z]", "")
            .toUpperCase(Locale.ROOT);
    String prefix = letters.length() >= 3 ? letters.substring(0, 3) : "UNI";
    for (int n = 1; ; n++) {
      String code = "%s-%02d".formatted(prefix, n);
      if (!taken.contains(code)) return code;
    }
  }

  public boolean belongsTo(Long tool) {
    return toolId.equals(tool);
  }

  public boolean isOperational() {
    return status == UnitStatus.OPERATIVA;
  }
}
