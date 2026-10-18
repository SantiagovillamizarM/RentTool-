package com.renttool.infrastructure.billing.web;

import com.renttool.application.billing.port.in.ManagePaymentsUseCase;
import com.renttool.domain.billing.Payment;
import com.renttool.domain.reservation.Participant;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Pagos y facturas", description = "Simulación escolar sin dinero real")
public class PaymentController {
  private final ManagePaymentsUseCase payments;

  public PaymentController(ManagePaymentsUseCase payments) {
    this.payments = payments;
  }

  public enum Result { APROBADO, RECHAZADO }
  public record PaymentRequest(@NotNull Result result) {}

  @PostMapping("/reservations/{id}/payments")
  @Operation(summary = "Simular un pago aprobado o rechazado")
  public Payment pay(
      @AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
      @Valid @RequestBody PaymentRequest request) {
    return payments.pay(actor(jwt), id, request.result() == Result.APROBADO);
  }

  @GetMapping("/reservations/{id}/payments")
  @Operation(summary = "Consultar pagos de demostración y factura")
  public ManagePaymentsUseCase.Summary get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
    return payments.get(actor(jwt), id);
  }

  @GetMapping("/reservations/{id}/invoice")
  @Operation(summary = "Descargar factura de demostración")
  public ResponseEntity<String> invoice(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
    var invoice = payments.invoice(actor(jwt), id);
    return ResponseEntity.ok().contentType(MediaType.TEXT_HTML)
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + invoice.number() + ".html\"")
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .header("X-Content-Type-Options", "nosniff")
        .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'")
        .body(InvoiceHtml.render(invoice));
  }

  private Participant actor(Jwt jwt) {
    return new Participant(
        Long.valueOf(jwt.getSubject()), Participant.Role.valueOf(jwt.getClaimAsString("role")));
  }
}
