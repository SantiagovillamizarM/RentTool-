package com.renttool.infrastructure.inventory.web;

import com.renttool.application.fleet.port.in.SetUnitCountUseCase;
import com.renttool.application.inventory.port.in.*;
import com.renttool.domain.inventory.*;
import com.renttool.application.shared.PageResult;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

/** Contratos HTTP de inventario. Validan formato; las reglas de negocio están en el dominio. */
public final class InventoryDtos {
  private InventoryDtos() {}

  public record ToolRequest(
      @Schema(example = "1") @NotNull Long categoryId,
      @Schema(example = "Taladro percutor 800 W") @NotBlank @Size(max = 160) String name,
      @Schema(example = "Incluye maletín, broca para concreto y mandril de 13 mm.")
          @NotBlank
          @Size(max = 3000)
          String description,
      @Schema(example = "25000", description = "Tarifa por día en pesos colombianos")
          @NotNull
          @DecimalMin("0.01")
          @Digits(integer = 12, fraction = 2)
          BigDecimal dailyRate,
      @Schema(example = "100000", description = "Garantía por unidad; opcional, 0 si no se pide")
          @PositiveOrZero
          @Digits(integer = 12, fraction = 2)
          BigDecimal deposit) {
    public ManageToolsUseCase.Command toCommand() {
      return new ManageToolsUseCase.Command(categoryId, name, description, dailyRate, deposit);
    }
  }

  public record UnitRequest(
      @Schema(example = "TAL-001", description = "Único dentro de la herramienta")
          @NotBlank
          @Size(max = 80)
          String code,
      @Schema(example = "OPERATIVA") @NotNull UnitStatus status) {
    public ManageUnitsUseCase.Command toCommand() {
      return new ManageUnitsUseCase.Command(code, status);
    }
  }

  public record ImageReferenceRequest(
      @Schema(example = "https://example.com/taladro.png") @NotBlank @Size(max = 1000)
          String location) {}

  public record ToolResponse(
      @Schema(example = "12") Long id,
      @Schema(example = "4") Long providerId,
      @Schema(example = "1") Long categoryId,
      @Schema(example = "Taladro percutor 800 W") String name,
      @Schema(example = "Incluye maletín, broca para concreto y mandril de 13 mm.")
          String description,
      @Schema(example = "25000.00") BigDecimal dailyRate,
      @Schema(example = "true") boolean active,
      @Schema(example = "100000.00", description = "Garantía por unidad") BigDecimal deposit) {
    public static ToolResponse from(Tool t) {
      return new ToolResponse(
          t.id(), t.providerId(), t.categoryId(), t.name(), t.description(), t.dailyRate(),
          t.active(), t.deposit());
    }
  }

  public record ToolDetailsResponse(
      @Schema(example = "12") Long id,
      @Schema(example = "Taladro percutor 800 W") String name,
      @Schema(example = "Incluye maletín, broca para concreto y mandril de 13 mm.")
          String description,
      @Schema(example = "1") Long categoryId,
      @Schema(example = "Eléctricas") String category,
      @Schema(example = "4") Long providerId,
      @Schema(example = "25000.00") BigDecimal dailyRate,
      @Schema(example = "true") boolean active,
      List<ImageResponse> images,
      @Schema(example = "3", description = "Unidades en estado OPERATIVA") long operationalUnits,
      @Schema(example = "100000.00", description = "Garantía por unidad") BigDecimal deposit) {
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
          d.operationalUnits(),
          t.deposit());
    }
  }

  public record UnitCountRequest(
      @Schema(example = "5", description = "Unidades que tiene, en mantenimiento incluidas")
          @NotNull
          @Min(0)
          @Max(SetUnitCountUseCase.MAX_UNITS)
          Integer count) {}

  public record UnitResponse(
      @Schema(example = "31") Long id,
      @Schema(example = "12") Long toolId,
      @Schema(example = "TAL-001") String code,
      @Schema(example = "OPERATIVA") UnitStatus status) {
    public static UnitResponse from(Unit u) {
      return new UnitResponse(u.id(), u.toolId(), u.code(), u.status());
    }
  }

  public record ImageResponse(
      @Schema(example = "8") Long id,
      @Schema(example = "12") Long toolId,
      @Schema(example = "/uploads/5f3c9a.png") String location) {
    public static ImageResponse from(ToolImage i) {
      return new ImageResponse(i.id(), i.toolId(), i.location());
    }
  }

  public record CategoryResponse(
      @Schema(example = "1") Long id,
      @Schema(example = "Eléctricas") String name,
      @Schema(example = "↯") String icon) {
    public static CategoryResponse from(Category c) {
      return new CategoryResponse(c.id(), c.name(), c.icon());
    }
  }

  public record PageResponse<T>(
      List<T> content,
      @Schema(example = "27") long totalElements,
      @Schema(example = "3") int totalPages,
      @Schema(example = "0", description = "Página actual, desde 0") int page) {
    public static <T> PageResponse<T> from(PageResult<T> p) {
      return new PageResponse<>(p.content(), p.totalElements(), p.totalPages(), p.page());
    }
  }
}
