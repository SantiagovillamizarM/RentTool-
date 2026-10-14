package com.renttool.application.inventory.port.in;

import com.renttool.domain.inventory.Tool;
import com.renttool.domain.inventory.ToolImage;
import java.util.List;

/** Vista de catálogo: la herramienta con su categoría, imágenes y unidades operativas. */
public record ToolDetails(
    Tool tool, String categoryName, List<ToolImage> images, long operationalUnits) {}
