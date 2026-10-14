package com.renttool.infrastructure.inventory.web;

import com.renttool.application.inventory.port.in.ManageUnitsUseCase;
import com.renttool.infrastructure.inventory.web.InventoryDtos.*;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tools/{toolId}/units")
public class UnitController {
  private final ManageUnitsUseCase units;

  public UnitController(ManageUnitsUseCase units) {
    this.units = units;
  }

  @GetMapping
  public List<UnitResponse> list(@PathVariable Long toolId, @AuthenticationPrincipal Jwt jwt) {
    return units.list(ActorResolver.from(jwt), toolId).stream().map(UnitResponse::from).toList();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public UnitResponse add(
      @PathVariable Long toolId,
      @Valid @RequestBody UnitRequest r,
      @AuthenticationPrincipal Jwt jwt) {
    return UnitResponse.from(units.add(ActorResolver.from(jwt), toolId, r.toCommand()));
  }

  @PutMapping("/{unitId}")
  public UnitResponse change(
      @PathVariable Long toolId,
      @PathVariable Long unitId,
      @Valid @RequestBody UnitRequest r,
      @AuthenticationPrincipal Jwt jwt) {
    return UnitResponse.from(
        units.change(ActorResolver.from(jwt), toolId, unitId, r.toCommand()));
  }

  @DeleteMapping("/{unitId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void retire(
      @PathVariable Long toolId, @PathVariable Long unitId, @AuthenticationPrincipal Jwt jwt) {
    units.retire(ActorResolver.from(jwt), toolId, unitId);
  }
}
