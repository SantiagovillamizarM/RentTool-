package com.renttool.domain.inventory;

import com.renttool.domain.shared.exception.InvalidInputException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Referencia a una imagen: URL HTTPS externa o recurso servido por la propia aplicación. Las
 * imágenes se muestran por {@code position} ascendente; la primera es la principal del catálogo.
 */
public record ToolImage(Long id, Long toolId, String location, int position) {
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
    return new ToolImage(null, toolId, location, 0);
  }

  /** Nueva imagen al final de las existentes (ordenadas como las devuelve el repositorio). */
  public static ToolImage append(Long toolId, String location, List<ToolImage> existing) {
    int last = existing.isEmpty() ? -1 : existing.getLast().position();
    return new ToolImage(null, toolId, location, last + 1);
  }

  /** Nueva imagen delante de todas: pasa a ser la principal. */
  public static ToolImage asMain(Long toolId, String location, List<ToolImage> existing) {
    int first = existing.isEmpty() ? 1 : existing.getFirst().position();
    return new ToolImage(null, toolId, location, first - 1);
  }

  public boolean belongsTo(Long tool) {
    return toolId.equals(tool);
  }
}
