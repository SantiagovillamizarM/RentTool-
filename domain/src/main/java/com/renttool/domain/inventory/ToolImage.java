package com.renttool.domain.inventory;

import com.renttool.domain.shared.exception.InvalidInputException;
import java.util.regex.Pattern;

/** Referencia a una imagen: URL HTTPS externa o recurso servido por la propia aplicación. */
public record ToolImage(Long id, Long toolId, String location) {
  private static final Pattern ALLOWED = Pattern.compile("https://\\S+|/(assets|uploads)/\\S+");
  private static final int MAX_LENGTH = 1000;

  public ToolImage {
    if (toolId == null) throw new InvalidInputException("La imagen requiere una herramienta");
    if (location == null
        || location.length() > MAX_LENGTH
        || !ALLOWED.matcher(location).matches())
      throw new InvalidInputException("La imagen debe ser una URL HTTPS o un recurso interno");
  }

  public static ToolImage attach(Long toolId, String location) {
    return new ToolImage(null, toolId, location);
  }

  public boolean belongsTo(Long tool) {
    return toolId.equals(tool);
  }
}
