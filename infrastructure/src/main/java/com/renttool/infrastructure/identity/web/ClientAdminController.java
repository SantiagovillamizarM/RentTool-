package com.renttool.infrastructure.identity.web;

import static com.renttool.infrastructure.config.OpenApiConfig.BEARER;

import com.renttool.application.identity.port.in.ManageClientsUseCase;
import com.renttool.application.shared.PageQuery;
import com.renttool.application.shared.PageResult;
import com.renttool.domain.identity.Account;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/clients")
@Tag(
    name = "Administración de clientes",
    description = "Solo ADMINISTRADOR. Lista de clientes registrados y control de acceso")
@SecurityRequirement(name = BEARER)
@ApiResponse(responseCode = "401", description = "Sin token, token vencido o cuenta inactiva")
@ApiResponse(responseCode = "403", description = "Quien llama no es ADMINISTRADOR")
public class ClientAdminController {
  private final ManageClientsUseCase clients;

  public ClientAdminController(ManageClientsUseCase clients) {
    this.clients = clients;
  }

  public record ClientResponse(
      Long id, String name, String email, String phone, boolean active) {
    static ClientResponse from(Account a) {
      return new ClientResponse(a.id(), a.name(), a.email(), a.phone(), a.active());
    }
  }

  public record ClientPage(
      List<ClientResponse> content, long totalElements, int totalPages, int page) {
    static ClientPage from(PageResult<Account> p) {
      return new ClientPage(
          p.content().stream().map(ClientResponse::from).toList(),
          p.totalElements(),
          p.totalPages(),
          p.page());
    }
  }

  public record StatusRequest(@NotNull Boolean active) {}

  @GetMapping
  @Operation(summary = "Listar clientes", description = "Incluye los inactivos.")
  @ApiResponse(responseCode = "200", description = "Página de clientes, el más reciente primero")
  public ClientPage list(
      @Parameter(description = "Página, desde 0") @RequestParam(defaultValue = "0") int page,
      @Parameter(description = "Tamaño de página, máximo " + PageQuery.MAX_SIZE)
          @RequestParam(defaultValue = "20")
          int size,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return ClientPage.from(clients.list(adminId(jwt), new PageQuery(page, size)));
  }

  @PutMapping("/{id}/status")
  @Operation(
      summary = "Activar o desactivar un cliente",
      description = "Un cliente inactivo no puede entrar y sus tokens vigentes dejan de servir.")
  @ApiResponse(responseCode = "200", description = "Estado actualizado")
  @ApiResponse(responseCode = "404", description = "No existe o no es cliente")
  public ClientResponse status(
      @PathVariable Long id,
      @Valid @RequestBody StatusRequest r,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return ClientResponse.from(clients.setActive(adminId(jwt), id, r.active()));
  }

  private static Long adminId(Jwt jwt) {
    return Long.valueOf(jwt.getSubject());
  }
}
