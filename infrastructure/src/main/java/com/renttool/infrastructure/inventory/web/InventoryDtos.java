package com.renttool.infrastructure.inventory.web;

import com.renttool.application.inventory.port.in.*;
import com.renttool.domain.inventory.*;
import com.renttool.application.shared.PageResult;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

/** Contratos HTTP de inventario. Validan formato; las reglas de negocio están en el dominio. */
public final class InventoryDtos {
  private InventoryDtos() {}

  public record ToolRequest(
      @NotNull Long categoryId,
      @NotBlank @Size(max = 160) String name,
      @NotBlank @Size(max = 3000) String description,
      @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal dailyRate) {
    public ManageToolsUseCase.Command toCommand() {
      return new ManageToolsUseCase.Command(categoryId, name, description, dailyRate);
    }
  }

  public record UnitRequest(@NotBlank @Size(max = 80) String code, @NotNull UnitStatus status) {
    public ManageUnitsUseCase.Command toCommand() {
      return new ManageUnitsUseCase.Command(code, status);
    }
  }

  public record ImageReferenceRequest(@NotBlank @Size(max = 1000) String location) {}

  public record ToolResponse(
      Long id,
      Long providerId,
      Long categoryId,
      String name,
      String description,
      BigDecimal dailyRate,
      boolean active) {
    public static ToolResponse from(Tool t) {
      return new ToolResponse(
          t.id(), t.providerId(), t.categoryId(), t.name(), t.description(), t.dailyRate(),
          t.active());
    }
  }

  public record ToolDetailsResponse(
      Long id,
      String name,
      String description,
      Long categoryId,
      String category,
      Long providerId,
      BigDecimal dailyRate,
      boolean active,
      List<ImageResponse> images,
      long operationalUnits) {
    public static ToolDetailsResponse from(ToolDetails d) {
      var t = d.tool();
      return new ToolDetailsResponse(
          t.id(),
          t.name(),
          t.description(),
          t.categoryId(),
          d.categoryName(),
          t.providerId(),
          t.dailyRate(),
          t.active(),
          d.images().stream().map(ImageResponse::from).toList(),
          d.operationalUnits());
    }
  }

  public record UnitResponse(Long id, Long toolId, String code, UnitStatus status) {
    public static UnitResponse from(Unit u) {
      return new UnitResponse(u.id(), u.toolId(), u.code(), u.status());
    }
  }

  public record ImageResponse(Long id, Long toolId, String location) {
    public static ImageResponse from(ToolImage i) {
      return new ImageResponse(i.id(), i.toolId(), i.location());
    }
  }

  public record CategoryResponse(Long id, String name, String icon) {
    public static CategoryResponse from(Category c) {
      return new CategoryResponse(c.id(), c.name(), c.icon());
    }
  }

  public record PageResponse<T>(List<T> content, long totalElements, int totalPages, int page) {
    public static <T> PageResponse<T> from(PageResult<T> p) {
      return new PageResponse<>(p.content(), p.totalElements(), p.totalPages(), p.page());
    }
  }
}
