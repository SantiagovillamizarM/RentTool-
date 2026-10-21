package com.renttool.infrastructure.billing.web;
import com.renttool.domain.billing.Invoice;
import org.springframework.web.util.HtmlUtils;
import java.time.temporal.ChronoUnit;
/** Se renderiza desde la copia histórica persistida, no desde los datos actuales del catálogo. */
final class InvoiceHtml {
  static String render(Invoice i) {
    return """
        <!doctype html><html lang="es"><meta charset="utf-8">
        <title>Factura %s</title>
        <style>body{font:16px Arial;max-width:850px;margin:48px auto;color:#222;padding:20px}
        h1{border-bottom:4px solid #ffda00;padding-bottom:20px}td,th{padding:12px;text-align:left;border-bottom:1px solid #ddd}
        table{width:100%%;border-collapse:collapse}.void{padding:16px;background:#ffe1e1;border:2px solid #c62828;color:#8e0000;font-weight:bold}</style>
        <h1>RentTool · Factura %s</h1>
        %s
        <p>Emitida: %s · Reserva #%s</p>
        <h2>Proveedor</h2><p>%s<br>NIT: %s<br>%s</p>
        <h2>Cliente</h2><p>%s<br>%s</p>
        <table><tr><th>Herramienta</th><th>Periodo</th><th>Días</th><th>Unidades</th><th>Tarifa COP/día</th></tr>
        <tr><td>%s</td><td>%s a %s</td><td>%s</td><td>%s</td><td>%s</td></tr></table>
        <h2>Total pagado: %s COP</h2>
        <p>Medio de pago: %s<br>Referencia: %s</p><p>La fecha de devolución no se cobra. El total reproduce el importe pactado de la reserva.</p>
        <p>Para guardar como PDF utiliza Imprimir → Guardar como PDF en tu navegador.</p></html>
        """.formatted(e(i.number()),e(i.number()),
          i.voidedAt() == null ? "" : "<p class=\"void\">FACTURA ANULADA el " + i.voidedAt()
              + ". " + e(i.voidReason()) + ". El pago se reembolsó al cliente.</p>",
          i.issuedAt(),i.reservationId(),
          e(i.sellerName()),e(i.sellerTaxId()),e(i.sellerAddress()),e(i.buyerName()),e(i.buyerEmail()),
          e(i.toolName()),i.start(),i.end(),ChronoUnit.DAYS.between(i.start(),i.end()),i.quantity(),
          i.dailyRate().toPlainString(),i.total().toPlainString(),
          e(i.paymentDetail() == null ? "Pago en línea" : i.paymentDetail()),e(i.paymentReference()));
  }
  private static String e(String value) { return HtmlUtils.htmlEscape(value==null?"":value); }
}
