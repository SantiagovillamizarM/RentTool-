package com.renttool.application.identity.port.in;

import com.renttool.application.identity.port.in.ManageProvidersUseCase.ProfileCommand;
import com.renttool.domain.identity.ProviderApplication;
import java.util.Optional;

/** Un cliente pide convertirse en proveedor y luego consulta la respuesta del administrador. */
public interface ApplyAsProviderUseCase {
  ProviderApplication submit(Long accountId, ProfileCommand profile);

  /** Su solicitud más reciente, si tiene alguna. */
  Optional<ProviderApplication> current(Long accountId);

  /** Marca la respuesta como vista para no volver a mostrarla. */
  void acknowledge(Long accountId);
}
