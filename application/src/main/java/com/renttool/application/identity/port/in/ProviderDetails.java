package com.renttool.application.identity.port.in;

import com.renttool.domain.identity.Account;
import com.renttool.domain.identity.ProviderProfile;

/**
 * Cuenta de proveedor con sus datos legales. {@code profile} es null en proveedores creados antes
 * de que el alta pasara por el administrador: este debe completarlos o desactivar la cuenta.
 */
public record ProviderDetails(Account account, ProviderProfile profile) {}
