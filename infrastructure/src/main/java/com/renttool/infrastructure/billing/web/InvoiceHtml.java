package com.renttool.infrastructure.billing.web;
import com.renttool.domain.billing.Invoice;
import org.springframework.web.util.HtmlUtils;
import java.time.temporal.ChronoUnit;
/** Se renderiza desde la copia histórica persistida, no desde los datos actuales del catálogo. */
final class InvoiceHtml {
  static String render(Invoice i) {
    return """
        <!doctype html><html lang="es"><meta charset="utf-8">
        <title>Factura de prueba %s</title>
        <style>body{font:16px Arial;max-width:850px;margin:48px auto;color:#222;padding:20px}
        h1{border-bottom:4px solid #ffda00;padding-bottom:20px}td,th{padding:12px;text-align:left;border-bottom:1px solid #ddd}
        table{width:100%%;border-collapse:collapse}.notice{padding:16px;background:#fff5c2}</style>
        <h1>RentTool · Factura de prueba %s</h1>
        <p class="notice">DEMOSTRACION ESCOLAR — Sin cobro real. Documento académico sin validez fiscal.</p>
        <p>Emitida: %s · Reserva #%s</p>
        <h2>Proveedor</h2><p>%s<br>NIT: %s<br>%s</p>
        <h2>Cliente</h2><p>%s<br>%s</p>
        <table><tr><th>Herramienta</th><th>Periodo</th><th>Días</th><th>Unidades</th><th>Tarifa COP/día</th></tr>
        <tr><td>%s</td><td>%s a %s</td><td>%s</td><td>%s</td><td>%s</td></tr></table>
        <h2>Total pagado de prueba: %s COP</h2>
        <p>Referencia: %s</p><p>La fecha de devolución no se cobra. El total reproduce el importe pactado de la reserva.</p>
        <p>Para guardar como PDF utiliza Imprimir → Guardar como PDF en tu navegador.</p></html>
        """.formatted(e(i.number()),e(i.number()),i.issuedAt(),i.reservationId(),
          e(i.sellerName()),e(i.sellerTaxId()),e(i.sellerAddress()),e(i.buyerName()),e(i.buyerEmail()),
          e(i.toolName()),i.start(),i.end(),ChronoUnit.DAYS.between(i.start(),i.end()),i.quantity(),
          i.dailyRate().toPlainString(),i.total().toPlainString(),e(i.paymentReference()));
  }
  private static String e(String value) { return HtmlUtils.htmlEscape(value==null?"":value); }
}
