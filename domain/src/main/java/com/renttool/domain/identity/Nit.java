package com.renttool.domain.identity;

import com.renttool.domain.shared.exception.InvalidInputException;

/**
 * NIT colombiano: número base y dígito de verificación. El dígito se comprueba con el algoritmo
 * de la DIAN (módulo 11 con pesos primos), que detecta la mayoría de errores de digitación.
 */
public record Nit(String number, int verificationDigit) {
  private static final int[] WEIGHTS = {3, 7, 13, 17, 19, 23, 29, 37, 41, 43, 47, 53, 59, 67, 71};

  public Nit {
    if (number == null || !number.strip().matches("\\d{5,15}"))
      throw new InvalidInputException("El NIT debe tener entre 5 y 15 dígitos, sin puntos");
    number = number.strip();
    if (verificationDigit != verificationDigitOf(number))
      throw new InvalidInputException("El dígito de verificación no corresponde al NIT");
  }

  public static int verificationDigitOf(String number) {
    int sum = 0;
    for (int i = 0; i < number.length(); i++)
      sum += (number.charAt(number.length() - 1 - i) - '0') * WEIGHTS[i];
    int remainder = sum % 11;
    return remainder > 1 ? 11 - remainder : remainder;
  }

  @Override
  public String toString() {
    return number + "-" + verificationDigit;
  }
}
