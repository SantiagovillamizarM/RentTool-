package com.renttool.infrastructure.billing.web;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;
import com.renttool.domain.billing.Invoice;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/** Factura en PDF. Se genera desde la copia histórica persistida, no desde el catálogo actual. */
final class InvoicePdf {
  private static final Locale ES = Locale.forLanguageTag("es-CO");
  private static final Color YELLOW = new Color(0xFF, 0xDA, 0x00);
  private static final Color GRAY = new Color(0x66, 0x66, 0x66);
  private static final Color LINE = new Color(0xDD, 0xDD, 0xDD);
  private static final Color RED = new Color(0xC6, 0x28, 0x28);
  private static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20);
  private static final Font HEADING = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
  private static final Font TEXT = FontFactory.getFont(FontFactory.HELVETICA, 10);
  private static final Font MUTED = FontFactory.getFont(FontFactory.HELVETICA, 9, GRAY);
  private static final Font BOLD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
  private static final Font TOTAL = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
  private static final Font VOID = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, RED);
  private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", ES);
  private static final DateTimeFormatter MOMENT =
      DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", ES);

  private InvoicePdf() {}

  static byte[] render(Invoice i, ZoneId zone) {
    var out = new ByteArrayOutputStream();
    var document = new Document(PageSize.A4, 48, 48, 48, 48);
    PdfWriter.getInstance(document, out);
    document.addTitle("Factura " + i.number());
    document.addAuthor("RentTool");
    document.open();

    document.add(new Paragraph("RentTool · Factura " + i.number(), TITLE));
    document.add(new Chunk(new LineSeparator(3, 100, YELLOW, Element.ALIGN_CENTER, -6)));
    document.add(paragraph(
        "Emitida: " + moment(i.issuedAt(), zone) + " · Reserva #" + i.reservationId(), MUTED, 14));

    if (i.voidedAt() != null) {
      var banner = new PdfPTable(1);
      banner.setWidthPercentage(100);
      banner.setSpacingBefore(12);
      var cell = new PdfPCell(new Phrase("FACTURA ANULADA el " + moment(i.voidedAt(), zone) + ": "
          + text(i.voidReason()) + ". El pago se reembolsó al cliente.", VOID));
      cell.setBackgroundColor(new Color(0xFF, 0xE1, 0xE1));
      cell.setBorderColor(RED);
      cell.setBorderWidth(1.5f);
      cell.setPadding(10);
      banner.addCell(cell);
      document.add(banner);
    }

    var parties = new PdfPTable(2);
    parties.setWidthPercentage(100);
    parties.setSpacingBefore(18);
    parties.addCell(party("Proveedor",
        text(i.sellerName()) + "\nNIT: " + text(i.sellerTaxId()) + "\n" + text(i.sellerAddress())));
    parties.addCell(party("Cliente", text(i.buyerName()) + "\n" + text(i.buyerEmail())));
    document.add(parties);

    var items = new PdfPTable(new float[] {3f, 2.6f, 0.8f, 1.3f, 1.7f});
    items.setWidthPercentage(100);
    items.setSpacingBefore(18);
    for (var title : new String[] {"Herramienta", "Periodo", "Días", "Unidades", "Tarifa por día"})
      items.addCell(header(title));
    items.addCell(cell(text(i.toolName()), Element.ALIGN_LEFT));
    items.addCell(cell(DAY.format(i.start()) + " a " + DAY.format(i.end()), Element.ALIGN_LEFT));
    items.addCell(cell(String.valueOf(ChronoUnit.DAYS.between(i.start(), i.end())), Element.ALIGN_RIGHT));
    items.addCell(cell(String.valueOf(i.quantity()), Element.ALIGN_RIGHT));
    items.addCell(cell(money(i.dailyRate()), Element.ALIGN_RIGHT));
    document.add(items);

    var total = paragraph("Total pagado: " + money(i.total()) + " COP", TOTAL, 0);
    total.setAlignment(Element.ALIGN_RIGHT);
    total.setSpacingBefore(14);
    document.add(total);

    document.add(paragraph("Medio de pago: "
        + text(i.paymentDetail() == null ? "Pago en línea" : i.paymentDetail())
        + "\nReferencia: " + text(i.paymentReference()), TEXT, 18));
    document.add(paragraph("La fecha de devolución no se cobra. El total reproduce el importe"
        + " pactado de la reserva. Factura de demostración: sin cobro real ni validez fiscal.",
        MUTED, 18));
    document.close();
    return out.toByteArray();
  }

  private static PdfPCell party(String title, String body) {
    var content = new Phrase();
    content.add(new Chunk(title + "\n", HEADING));
    content.add(new Chunk(body, TEXT));
    var cell = new PdfPCell(content);
    cell.setBorder(Rectangle.NO_BORDER);
    cell.setPaddingRight(12);
    cell.setLeading(0, 1.4f);
    return cell;
  }

  private static PdfPCell header(String title) {
    var cell = new PdfPCell(new Phrase(title, BOLD));
    cell.setBorder(Rectangle.BOTTOM);
    cell.setBorderColor(YELLOW);
    cell.setBorderWidth(2);
    cell.setPadding(8);
    return cell;
  }

  private static PdfPCell cell(String value, int alignment) {
    var cell = new PdfPCell(new Phrase(value, TEXT));
    cell.setBorder(Rectangle.BOTTOM);
    cell.setBorderColor(LINE);
    cell.setPadding(8);
    cell.setHorizontalAlignment(alignment);
    return cell;
  }

  private static Paragraph paragraph(String value, Font font, float spacingBefore) {
    var paragraph = new Paragraph(value, font);
    paragraph.setSpacingBefore(spacingBefore);
    paragraph.setLeading(0, 1.4f);
    return paragraph;
  }

  private static String money(BigDecimal amount) {
    var format = NumberFormat.getCurrencyInstance(ES);
    format.setMaximumFractionDigits(0);
    return format.format(amount);
  }

  private static String moment(Instant instant, ZoneId zone) {
    return MOMENT.format(instant.atZone(zone));
  }

  private static String text(String value) {
    return value == null ? "" : value;
  }
}
