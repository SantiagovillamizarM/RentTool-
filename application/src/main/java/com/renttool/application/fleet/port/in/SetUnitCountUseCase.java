package com.renttool.application.fleet.port.in;

import com.renttool.domain.inventory.Actor;
import com.renttool.domain.inventory.Unit;
import java.util.List;

/**
 * El proveedor indica cuántas unidades tiene de una herramienta y el sistema crea o retira las que
 * hagan falta. Las unidades en mantenimiento cuentan: siguen siendo suyas aunque no se puedan
 * reservar por ahora. Nunca retira una unidad asignada a una reserva aceptada que no ha terminado.
 */
public interface SetUnitCountUseCase {
  int MAX_UNITS = 200;

  /** Devuelve todas las unidades de la herramienta tras el cambio, retiradas incluidas. */
  List<Unit> setCount(Actor actor, Long toolId, int count);
}
