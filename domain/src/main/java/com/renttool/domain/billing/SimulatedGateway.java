package com.renttool.domain.billing;

import com.renttool.domain.shared.exception.InvalidInputException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

/**
 * Pasarela de pagos simulada para el proyecto escolar. Valida los datos como lo haría un banco y
 * decide el resultado con datos de prueba, igual que los entornos de prueba de las pasarelas
 * reales. Nunca devuelve ni conserva el número completo de la tarjeta ni el CVV: solo la franquicia
 * y los últimos cuatro dígitos.
 */
public final class SimulatedGateway {
  /** Tarjetas de prueba admitidas y el motivo de rechazo de cada una; vacío significa aprobada. */
  public static final Map<String, String> TEST_CARDS = Map.of(
      "4242424242424242", "",
      "5555555555554444", "",
      "4000000000000002", "Fondos insuficientes",
      "4000000000000127", "El banco rechazó la transacción");
  /** Celular de prueba que Nequi rechaza; cualquier otro celular válido se aprueba. */
  public static final String NEQUI_DECLINED = "3992222222";

  public enum Method { TARJETA, NEQUI }

  /**
   * @param detail lo que se puede mostrar y guardar, por ejemplo «Visa •••• 4242»
   * @param message motivo del rechazo; null si se aprobó
   */
  public record Authorization(Method method, String detail, boolean approved, String message) {}

  private SimulatedGateway() {}

  public static Authorization card(String number, String holder, int month, int year, String cvv,
      LocalDate today) {
    var digits = number == null ? "" : number.replaceAll("[\\s-]", "");
    if (!digits.matches("\\d{13,19}") || !luhn(digits))
      throw new InvalidInputException("El número de la tarjeta no es válido");
    if (holder == null || holder.isBlank())
      throw new InvalidInputException("Escribe el nombre que aparece en la tarjeta");
    if (month < 1 || month > 12)
      throw new InvalidInputException("El mes de vencimiento no es válido");
    if (YearMonth.of(year < 100 ? 2000 + year : year, month).isBefore(YearMonth.from(today)))
      throw new InvalidInputException("La tarjeta está vencida");
    if (cvv == null || !cvv.matches("\\d{3,4}"))
      throw new InvalidInputException("El código de seguridad debe tener 3 o 4 dígitos");
    var outcome = TEST_CARDS.get(digits);
    if (outcome == null)
      throw new InvalidInputException(
          "Tarjeta no aceptada. Usa una de las tarjetas aceptadas que aparecen en el formulario");
    var detail = brand(digits) + " •••• " + digits.substring(digits.length() - 4);
    return new Authorization(Method.TARJETA, detail, outcome.isEmpty(), outcome.isEmpty() ? null : outcome);
  }

  public static Authorization nequi(String phone) {
    var digits = phone == null ? "" : phone.replaceAll("[\\s-]", "");
    if (!digits.matches("3\\d{9}"))
      throw new InvalidInputException("El celular de Nequi debe tener 10 dígitos y empezar por 3");
    var detail = "Nequi •••• " + digits.substring(6);
    boolean approved = !NEQUI_DECLINED.equals(digits);
    return new Authorization(Method.NEQUI, detail, approved,
        approved ? null : "El pago no fue aprobado en la app de Nequi");
  }

  static String brand(String digits) {
    if (digits.startsWith("4")) return "Visa";
    int two = Integer.parseInt(digits.substring(0, 2)), four = Integer.parseInt(digits.substring(0, 4));
    if ((two >= 51 && two <= 55) || (four >= 2221 && four <= 2720)) return "Mastercard";
    if (two == 34 || two == 37) return "American Express";
    return "Tarjeta";
  }

  /** Dígito de control de las tarjetas (algoritmo de Luhn). */
  static boolean luhn(String digits) {
    int sum = 0;
    for (int i = 0; i < digits.length(); i++) {
      int d = digits.charAt(digits.length() - 1 - i) - '0';
      if (i % 2 == 1) { d *= 2; if (d > 9) d -= 9; }
      sum += d;
    }
    return sum % 10 == 0;
  }
}
