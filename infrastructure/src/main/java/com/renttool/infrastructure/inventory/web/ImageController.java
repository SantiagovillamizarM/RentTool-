package com.renttool.infrastructure.inventory.web;

import static com.renttool.infrastructure.config.OpenApiConfig.BEARER;

import com.renttool.application.inventory.port.in.ManageImagesUseCase;
import com.renttool.infrastructure.inventory.web.InventoryDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/tools/{toolId}/images")
@Tag(name = "Imágenes", description = "Fotos de una herramienta propia")
@SecurityRequirement(name = BEARER)
@ApiResponse(responseCode = "401", description = "Sin token, token vencido o cuenta inactiva")
@ApiResponse(responseCode = "403", description = "No es PROVEEDOR o la herramienta es de otro")
@ApiResponse(responseCode = "404", description = "La herramienta o la imagen no existe")
public class ImageController {
  private final ManageImagesUseCase images;

  public ImageController(ManageImagesUseCase images) {
    this.images = images;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Agregar una imagen por URL",
      description = "Solo se aceptan URLs HTTPS o recursos internos (/assets, /uploads).")
  @ApiResponse(responseCode = "201", description = "Imagen agregada")
  @ApiResponse(responseCode = "400", description = "La URL no es HTTPS ni un recurso interno")
  public ImageResponse addReference(
      @PathVariable Long toolId,
      @Valid @RequestBody ImageReferenceRequest r,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return ImageResponse.from(images.addReference(ActorResolver.from(jwt), toolId, r.location()));
  }

  @PostMapping(value = "/upload", consumes = "multipart/form-data")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Subir una imagen",
      description = "Archivo PNG o JPEG real de hasta 5 MB, enviado en el campo `file`.")
  @ApiResponse(responseCode = "201", description = "Imagen subida")
  @ApiResponse(responseCode = "400", description = "No es una imagen válida o supera 5 MB")
  public ImageResponse upload(
      @PathVariable Long toolId,
      @RequestParam MultipartFile file,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt)
      throws IOException {
    return ImageResponse.from(images.upload(ActorResolver.from(jwt), toolId, file.getBytes()));
  }

  @PostMapping(value = "/main", consumes = "multipart/form-data")
  @Operation(
      summary = "Cambiar la imagen principal",
      description =
          "Sube un PNG o JPEG de hasta 5 MB en el campo `file` y lo pone como imagen principal"
              + " del catálogo en lugar de la actual. Las demás imágenes se conservan.")
  @ApiResponse(responseCode = "200", description = "Imagen principal reemplazada")
  @ApiResponse(responseCode = "400", description = "No es una imagen válida o supera 5 MB")
  public ImageResponse replaceMain(
      @PathVariable Long toolId,
      @RequestParam MultipartFile file,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt)
      throws IOException {
    return ImageResponse.from(
        images.replaceMain(ActorResolver.from(jwt), toolId, file.getBytes()));
  }

  @DeleteMapping("/{imageId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Quitar una imagen")
  @ApiResponse(responseCode = "204", description = "Imagen quitada")
  public void remove(
      @PathVariable Long toolId,
      @PathVariable Long imageId,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    images.remove(ActorResolver.from(jwt), toolId, imageId);
  }
}
