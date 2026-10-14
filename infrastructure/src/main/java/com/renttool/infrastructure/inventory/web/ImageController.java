package com.renttool.infrastructure.inventory.web;

import com.renttool.application.inventory.port.in.ManageImagesUseCase;
import com.renttool.infrastructure.inventory.web.InventoryDtos.*;
import jakarta.validation.Valid;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/tools/{toolId}/images")
public class ImageController {
  private final ManageImagesUseCase images;

  public ImageController(ManageImagesUseCase images) {
    this.images = images;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ImageResponse addReference(
      @PathVariable Long toolId,
      @Valid @RequestBody ImageReferenceRequest r,
      @AuthenticationPrincipal Jwt jwt) {
    return ImageResponse.from(images.addReference(ActorResolver.from(jwt), toolId, r.location()));
  }

  @PostMapping(value = "/upload", consumes = "multipart/form-data")
  @ResponseStatus(HttpStatus.CREATED)
  public ImageResponse upload(
      @PathVariable Long toolId,
      @RequestParam("file") MultipartFile file,
      @AuthenticationPrincipal Jwt jwt)
      throws IOException {
    return ImageResponse.from(images.upload(ActorResolver.from(jwt), toolId, file.getBytes()));
  }

  @DeleteMapping("/{imageId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void remove(
      @PathVariable Long toolId, @PathVariable Long imageId, @AuthenticationPrincipal Jwt jwt) {
    images.remove(ActorResolver.from(jwt), toolId, imageId);
  }
}
