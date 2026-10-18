package com.renttool.application.identity.port.in;

import java.util.List;

/**
 * El proveedor mantiene al día los datos de su empresa: nombre, representante, contacto y logo. El
 * NIT y el tipo de persona no cambian por aquí: son los datos con que el administrador verificó la
 * empresa, y solo él puede corregirlos.
 */
public interface ManageOwnCompanyUseCase {
  ProviderDetails updateOwn(Long providerId, CompanyCommand command);

  ProviderDetails setOwnLogo(Long providerId, byte[] content);

  ProviderDetails removeOwnLogo(Long providerId);

  /** Proveedores activos, para que los clientes filtren el catálogo por proveedor. */
  List<PublicProvider> publicProviders();

  record CompanyCommand(
      String legalName,
      String tradeName,
      String legalRepresentative,
      String representativeDocument,
      String phone,
      String address,
      String city) {}

  record PublicProvider(Long id, String name, String logoUrl) {}
}
