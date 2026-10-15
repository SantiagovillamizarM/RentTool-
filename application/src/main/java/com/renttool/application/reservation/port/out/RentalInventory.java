package com.renttool.application.reservation.port.out;

import com.renttool.domain.reservation.RentableTool;
import java.util.List;
import java.util.Optional;

/** Lo que reservas consulta del inventario sin depender de su modelo. */
public interface RentalInventory {
  Optional<RentableTool> find(Long toolId);

  /**
   * Igual que find, pero bloquea la herramienta hasta el fin de la transacción. Toda asignación de
   * unidades pasa por aquí, así dos aceptaciones simultáneas no pueden repartir la misma unidad.
   */
  Optional<RentableTool> lockForAllocation(Long toolId);

  /** Unidades en estado OPERATIVA, en orden estable. */
  List<Long> operationalUnitIds(Long toolId);

  List<Long> toolIdsOf(Long providerId);
}
