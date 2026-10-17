package com.renttool.application.identity.port.out;

import com.renttool.application.shared.PageQuery;
import com.renttool.application.shared.PageResult;
import com.renttool.domain.identity.ProviderApplication;
import com.renttool.domain.identity.ProviderApplication.Status;
import java.util.Optional;

public interface ProviderApplicationRepository {
  ProviderApplication save(ProviderApplication application);

  /** Bloquea la solicitud hasta el fin de la transacción: dos revisiones no la resuelven a la vez. */
  Optional<ProviderApplication> findByIdForUpdate(Long id);

  /** La más reciente de la cuenta, en cualquier estado. */
  Optional<ProviderApplication> findLatestByAccount(Long accountId);

  boolean existsPendingWithNit(String nit);

  /** {@code status} null incluye todas; las más antiguas primero, para atenderlas en orden. */
  PageResult<ProviderApplication> findByStatus(Status status, PageQuery page);
}
