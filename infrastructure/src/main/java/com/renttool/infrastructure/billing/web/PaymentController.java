package com.renttool.infrastructure.billing.web;

import com.renttool.application.billing.port.in.ManagePaymentsUseCase;
import com.renttool.domain.billing.Payment;
import com.renttool.domain.reservation.Participant;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
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
  private final com.renttool.application.reservation.port.in.ExpireReservationsUseCase expiry;

  public PaymentController(ManagePaymentsUseCase payments,
      com.renttool.application.reservation.port.in.ExpireReservationsUseCase expiry) {
    this.payments = payments;
    this.expiry = expiry;
  }

  /**
   * Datos de la pasarela simulada. Con TARJETA: number, holder, expMonth, expYear y cvv. Con NEQUI:
   * phone. No se guardan: se validan y solo se conserva el detalle enmascarado.
   */
  public record PaymentRequest(
      @NotNull ManagePaymentsUseCase.Checkout.Method method,
      @Schema(example = "4242 4242 4242 4242") String number,
      @Schema(example = "ANA RUIZ") String holder,
      @Schema(example = "12") Integer expMonth,
      @Schema(example = "2030") Integer expYear,
      @Schema(example = "123") String cvv,
      @Schema(example = "3991111111") String phone) {
    ManagePaymentsUseCase.Checkout toCheckout() {
      return new ManagePaymentsUseCase.Checkout(method, number, holder,
          expMonth == null ? 0 : expMonth, expYear == null ? 0 : expYear, cvv, phone);
    }
  }

  @PostMapping("/reservations/{id}/payments")
  @Operation(summary = "Pagar una reserva con la pasarela simulada",
      description = "Tarjetas de prueba: 4242 4242 4242 4242 y 5555 5555 5555 4444 se aprueban; "
          + "4000 0000 0000 0002 y 4000 0000 0000 0127 se rechazan. Nequi: 399 222 2222 se rechaza, "
          + "cualquier otro celular válido se aprueba.")
  public Payment pay(
      @AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
      @Valid @RequestBody PaymentRequest request) {
    // El plazo se respeta al momento, sin esperar la revisión periódica de vencimientos.
    if (expiry.expireIfDue(id))
      throw new com.renttool.domain.shared.exception.ConflictException(
          "El plazo para pagar esta solicitud ya venció");
    return payments.pay(actor(jwt), id, request.toCheckout());
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
