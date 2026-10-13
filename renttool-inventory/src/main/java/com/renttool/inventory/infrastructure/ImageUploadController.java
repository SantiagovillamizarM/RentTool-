package com.renttool.inventory.infrastructure;

import com.renttool.inventory.application.*;
import com.renttool.shared.BusinessException;
import java.io.*;
import java.nio.file.*;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/tools")
public class ImageUploadController {
  private final InventoryService service;
  private final JpaImages images;
  private final Path directory;

  public ImageUploadController(
      ToolRepository tools, JpaImages images, @Value("${renttool.upload-dir:uploads}") String dir) {
    service = new InventoryService(tools);
    this.images = images;
    directory = Path.of(dir).toAbsolutePath().normalize();
  }

  @PostMapping(value = "/{id}/images/upload", consumes = "multipart/form-data")
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public ImageEntity upload(
      @PathVariable Long id,
      @RequestParam("file") MultipartFile file,
      @AuthenticationPrincipal Jwt jwt)
      throws IOException {
    if (jwt == null
        || !("PROVEEDOR".equals(jwt.getClaimAsString("role"))
            || "ADMINISTRADOR".equals(jwt.getClaimAsString("role"))))
      throw new BusinessException(403, "Se requiere rol proveedor");
    service.owned(
        id, Long.valueOf(jwt.getSubject()), "ADMINISTRADOR".equals(jwt.getClaimAsString("role")));
    if (file.isEmpty() || file.getSize() > 5 * 1024 * 1024)
      throw new BusinessException(400, "Selecciona una imagen de hasta 5 MB");
    var bytes = file.getBytes();
    try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
      var readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) throw new BusinessException(400, "La imagen debe ser PNG o JPEG");
      var reader = readers.next();
      try {
        reader.setInput(input);
        String format = reader.getFormatName();
        if (!format.equalsIgnoreCase("png") && !format.equalsIgnoreCase("jpeg"))
          throw new BusinessException(400, "La imagen debe ser PNG o JPEG");
        if ((long) reader.getWidth(0) * reader.getHeight(0) > 16_000_000)
          throw new BusinessException(400, "La imagen supera 16 megapíxeles");
        var image = reader.read(0);
        Files.createDirectories(directory);
        String name = UUID.randomUUID() + ".png";
        Path target = directory.resolve(name);
        ImageIO.write(image, "png", target.toFile());
        var entity = new ImageEntity();
        entity.toolId = id;
        entity.location = "/uploads/" + name;
        try {
          return images.save(entity);
        } catch (RuntimeException e) {
          Files.deleteIfExists(target);
          throw e;
        }
      } finally {
        reader.dispose();
      }
    }
  }
}
