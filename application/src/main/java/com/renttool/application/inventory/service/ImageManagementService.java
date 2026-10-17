package com.renttool.application.inventory.service;

import com.renttool.application.inventory.port.in.ManageImagesUseCase;
import com.renttool.application.inventory.port.out.*;
import com.renttool.domain.inventory.*;
import com.renttool.domain.shared.exception.*;
import java.util.function.Function;

public class ImageManagementService implements ManageImagesUseCase {
  public static final int MAX_UPLOAD_BYTES = 5 * 1024 * 1024;

  private final ToolImageRepository images;
  private final ImageStorage storage;
  private final ToolAccess access;

  public ImageManagementService(
      ToolRepository tools, ToolImageRepository images, ImageStorage storage) {
    this.images = images;
    this.storage = storage;
    this.access = new ToolAccess(tools);
  }

  @Override
  public ToolImage addReference(Actor actor, Long toolId, String location) {
    access.manageable(toolId, actor);
    return images.save(ToolImage.append(toolId, location, images.findByToolId(toolId)));
  }

  @Override
  public ToolImage upload(Actor actor, Long toolId, byte[] content) {
    access.manageable(toolId, actor);
    return store(
        content, location -> ToolImage.append(toolId, location, images.findByToolId(toolId)));
  }

  @Override
  public ToolImage replaceMain(Actor actor, Long toolId, byte[] content) {
    access.manageable(toolId, actor);
    var current = images.findByToolId(toolId);
    var saved = store(content, location -> ToolImage.asMain(toolId, location, current));
    if (!current.isEmpty()) images.delete(current.getFirst());
    return saved;
  }

  /** Guarda el archivo y su referencia; si la referencia falla, no deja el archivo huérfano. */
  private ToolImage store(byte[] content, Function<String, ToolImage> reference) {
    if (content == null || content.length == 0 || content.length > MAX_UPLOAD_BYTES)
      throw new InvalidInputException("Selecciona una imagen de hasta 5 MB");
    String location = storage.store(content);
    try {
      return images.save(reference.apply(location));
    } catch (RuntimeException e) {
      storage.delete(location);
      throw e;
    }
  }

  @Override
  public void remove(Actor actor, Long toolId, Long imageId) {
    access.manageable(toolId, actor);
    var image =
        images
            .findById(imageId)
            .filter(i -> i.belongsTo(toolId))
            .orElseThrow(() -> new NotFoundException("Imagen no encontrada"));
    images.delete(image);
  }
}
