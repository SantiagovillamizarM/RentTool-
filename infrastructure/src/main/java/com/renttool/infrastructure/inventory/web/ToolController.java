package com.renttool.infrastructure.inventory.web;

import com.renttool.application.inventory.port.in.ManageToolsUseCase;
import com.renttool.infrastructure.inventory.web.InventoryDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tools")
public class ToolController {
  private final ManageToolsUseCase tools;

  public ToolController(ManageToolsUseCase tools) {
    this.tools = tools;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ToolResponse publish(@Valid @RequestBody ToolRequest r, @AuthenticationPrincipal Jwt jwt) {
    return ToolResponse.from(tools.publish(ActorResolver.from(jwt), r.toCommand()));
  }

  @PutMapping("/{id}")
  public ToolResponse edit(
      @PathVariable Long id, @Valid @RequestBody ToolRequest r, @AuthenticationPrincipal Jwt jwt) {
    return ToolResponse.from(tools.edit(ActorResolver.from(jwt), id, r.toCommand()));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void retire(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
    tools.retire(ActorResolver.from(jwt), id);
  }
}
