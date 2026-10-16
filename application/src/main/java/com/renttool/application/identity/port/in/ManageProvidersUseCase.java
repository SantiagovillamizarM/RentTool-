package com.renttool.application.identity.port.in;

import com.renttool.application.shared.PageQuery;
import com.renttool.application.shared.PageResult;
import com.renttool.domain.identity.PersonType;

/**
 * Alta y gestión de proveedores. Solo un ADMINISTRADOR puede ejecutarlo: el registro público ya
 * no crea proveedores, para que nadie publique inventario sin estar identificado.
 */
public interface ManageProvidersUseCase {
  ProviderDetails register(Long adminId, RegisterCommand command);

  PageResult<ProviderDetails> list(Long adminId, PageQuery page);

  ProviderDetails get(Long adminId, Long providerId);

  ProviderDetails update(Long adminId, Long providerId, ProfileCommand command);

  ProviderDetails setActive(Long adminId, Long providerId, boolean active);

  /** Sube o reemplaza el logo (PNG o JPEG). Requiere que el proveedor tenga datos legales. */
  ProviderDetails setLogo(Long adminId, Long providerId, byte[] content);

  /** Quita el logo; la interfaz vuelve a mostrar el predeterminado. */
  ProviderDetails removeLogo(Long adminId, Long providerId);

  /** Asigna una contraseña temporal que el proveedor deberá cambiar al entrar. */
  void resetPassword(Long adminId, Long providerId, String temporaryPassword);

  record RegisterCommand(String email, String temporaryPassword, ProfileCommand profile) {}

  record ProfileCommand(
      PersonType personType,
      String nit,
      int verificationDigit,
      String legalName,
      String tradeName,
      String legalRepresentative,
      String representativeDocument,
      String phone,
      String address,
      String city) {}
}
