package com.renttool.application.favorite.port.in;

import com.renttool.application.inventory.port.in.ToolDetails;
import com.renttool.domain.reservation.Participant;
import java.util.List;

/** Lista de deseos del cliente. Guardar y quitar son idempotentes. */
public interface ManageFavoritesUseCase {
  /** Favoritos aún publicados, el más reciente primero; los retirados no se muestran. */
  List<ToolDetails> list(Participant client);

  void add(Participant client, Long toolId);

  void remove(Participant client, Long toolId);
}
