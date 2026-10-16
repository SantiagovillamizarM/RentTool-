package com.renttool.infrastructure.reservation.web;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * CSV que Excel en español abre con doble clic: BOM UTF-8 para las tildes, punto y coma como
 * separador y coma decimal. Los textos que empiezan por = + - @ se escapan para que una hoja de
 * cálculo no los ejecute como fórmula (los nombres los escriben los usuarios).
 */
final class Csv {
  private static final String BOM = "﻿";
  private static final String FORMULA_START = "=+-@\t\r";
  private final StringBuilder out = new StringBuilder(BOM);

  Csv row(List<?> values) {
    for (int i = 0; i < values.size(); i++) {
      if (i > 0) out.append(';');
      out.append(cell(values.get(i)));
    }
    out.append("\r\n");
    return this;
  }

  byte[] bytes() {
    return out.toString().getBytes(StandardCharsets.UTF_8);
  }

  static String cell(Object value) {
    if (value == null) return "";
    if (value instanceof BigDecimal n) return n.toPlainString().replace('.', ',');
    String text = value.toString();
    if (!text.isEmpty() && FORMULA_START.indexOf(text.charAt(0)) >= 0) text = "'" + text;
    if (text.contains(";") || text.contains("\"") || text.contains("\n") || text.contains("\r"))
      text = '"' + text.replace("\"", "\"\"") + '"';
    return text;
  }
}
