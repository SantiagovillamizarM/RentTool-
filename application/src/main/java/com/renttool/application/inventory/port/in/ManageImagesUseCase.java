package com.renttool.application.inventory.port.in;

import com.renttool.domain.inventory.Actor;
import com.renttool.domain.inventory.ToolImage;

public interface ManageImagesUseCase {
  ToolImage addReference(Actor actor, Long toolId, String location);

  ToolImage upload(Actor actor, Long toolId, byte[] content);

  void remove(Actor actor, Long toolId, Long imageId);

  /** Sube una imagen y la pone como principal en lugar de la actual; las demás se conservan. */
  ToolImage replaceMain(Actor actor, Long toolId, byte[] content);
}
