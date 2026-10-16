package com.renttool.infrastructure.storage;

import com.renttool.application.identity.port.out.LogoStorage;
import com.renttool.application.inventory.port.out.ImageStorage;
import com.renttool.domain.shared.exception.InvalidInputException;
import java.io.*;
import java.nio.file.*;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Guarda imágenes en disco local. Decodifica el contenido para aceptar solo PNG o JPEG reales y lo
 * vuelve a codificar como PNG, descartando metadatos y contenido ajeno a la imagen. Sirve tanto las
 * fotos del inventario como los logos de proveedores; por eso vive fuera de ambos contextos.
 */
@Component
class LocalImageStorage implements ImageStorage, LogoStorage {
  static final String PUBLIC_PREFIX = "/uploads/";
  private static final long MAX_PIXELS = 16_000_000;

  private final Path directory;

  LocalImageStorage(@Value("${renttool.upload-dir:uploads}") String dir) {
    directory = Path.of(dir).toAbsolutePath().normalize();
  }

  @Override
  public String store(byte[] content) {
    try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
      var readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) throw new InvalidInputException("La imagen debe ser PNG o JPEG");
      var reader = readers.next();
      try {
        reader.setInput(input);
        String format = reader.getFormatName();
        if (!format.equalsIgnoreCase("png") && !format.equalsIgnoreCase("jpeg"))
          throw new InvalidInputException("La imagen debe ser PNG o JPEG");
        if ((long) reader.getWidth(0) * reader.getHeight(0) > MAX_PIXELS)
          throw new InvalidInputException("La imagen supera 16 megapíxeles");
        var image = reader.read(0);
        Files.createDirectories(directory);
        String name = UUID.randomUUID() + ".png";
        ImageIO.write(image, "png", directory.resolve(name).toFile());
        return PUBLIC_PREFIX + name;
      } finally {
        reader.dispose();
      }
    } catch (IOException e) {
      throw new UncheckedIOException("No se pudo guardar la imagen", e);
    }
  }

  @Override
  public void delete(String location) {
    if (!location.startsWith(PUBLIC_PREFIX)) return;
    Path target = directory.resolve(location.substring(PUBLIC_PREFIX.length())).normalize();
    if (!target.startsWith(directory)) return;
    try {
      Files.deleteIfExists(target);
    } catch (IOException e) {
      throw new UncheckedIOException("No se pudo eliminar la imagen", e);
    }
  }
}
