package com.renttool.domain.identity;

import com.renttool.domain.shared.exception.InvalidInputException;

/**
 * Datos con que se identifica legalmente a un proveedor. Solo el administrador los registra o
 * modifica; la cuenta de acceso vive aparte en {@link Account}. {@code logoLocation} es opcional:
 * sin logo, la interfaz muestra uno predeterminado.
 */
public record ProviderProfile(
    Long accountId,
    PersonType personType,
    Nit nit,
    String legalName,
    String tradeName,
    String legalRepresentative,
    String representativeDocument,
    String phone,
    String address,
    String city,
    String logoLocation) {
  public ProviderProfile {
    if (personType == null) throw new InvalidInputException("El tipo de persona es obligatorio");
    if (nit == null) throw new InvalidInputException("El NIT es obligatorio");
    legalName = required(legalName, "La razón social o nombre completo es obligatorio");
    tradeName = optional(tradeName);
    legalRepresentative = optional(legalRepresentative);
    representativeDocument = optional(representativeDocument);
    if (personType == PersonType.JURIDICA
        && (legalRepresentative == null || representativeDocument == null))
      throw new InvalidInputException(
          "Una persona jurídica requiere representante legal y su documento");
    phone = required(phone, "El teléfono es obligatorio");
    if (!phone.matches("\\+?[\\d ]{7,20}"))
      throw new InvalidInputException("El teléfono solo admite dígitos, espacios y +");
    address = required(address, "La dirección es obligatoria");
    city = required(city, "La ciudad es obligatoria");
    logoLocation = optional(logoLocation);
    if (logoLocation != null && !logoLocation.startsWith("/uploads/"))
      throw new InvalidInputException("El logo debe ser una imagen subida a RentTool");
  }

  public ProviderProfile withAccount(Long newAccountId) {
    return new ProviderProfile(
        newAccountId,
        personType,
        nit,
        legalName,
        tradeName,
        legalRepresentative,
        representativeDocument,
        phone,
        address,
        city,
        logoLocation);
  }

  public ProviderProfile withLogo(String newLogoLocation) {
    return new ProviderProfile(
        accountId,
        personType,
        nit,
        legalName,
        tradeName,
        legalRepresentative,
        representativeDocument,
        phone,
        address,
        city,
        newLogoLocation);
  }

  /** Nombre con que el proveedor aparece en la plataforma. */
  public String displayName() {
    return tradeName != null ? tradeName : legalName;
  }

  private static String required(String value, String message) {
    if (value == null || value.isBlank()) throw new InvalidInputException(message);
    return value.strip();
  }

  private static String optional(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }
}
