package com.renttool.application.identity.service;

import com.renttool.application.identity.port.in.ManageProvidersUseCase.ProfileCommand;
import com.renttool.domain.identity.Nit;
import com.renttool.domain.identity.ProviderProfile;
import com.renttool.domain.shared.exception.InvalidInputException;

/** Convierte los datos legales recibidos en un perfil validado por el dominio. */
final class ProviderProfiles {
  private ProviderProfiles() {}

  static ProviderProfile from(Long accountId, ProfileCommand c) {
    if (c == null) throw new InvalidInputException("Los datos del proveedor son obligatorios");
    return new ProviderProfile(
        accountId,
        c.personType(),
        new Nit(c.nit(), c.verificationDigit()),
        c.legalName(),
        c.tradeName(),
        c.legalRepresentative(),
        c.representativeDocument(),
        c.phone(),
        c.address(),
        c.city(),
        null);
  }
}
