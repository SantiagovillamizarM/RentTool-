package com.renttool.application.identity.port.in;

import com.renttool.application.shared.PageQuery;
import com.renttool.application.shared.PageResult;
import com.renttool.domain.identity.Account;
import com.renttool.domain.identity.ProviderApplication;
import com.renttool.domain.identity.ProviderApplication.Status;

/** El administrador revisa las solicitudes para ser proveedor. */
public interface ReviewProviderApplicationsUseCase {
  PageResult<ApplicationDetails> list(Long adminId, Status status, PageQuery page);

  /** Convierte la cuenta en proveedor con los datos de la solicitud. */
  ApplicationDetails accept(Long adminId, Long applicationId);

  ApplicationDetails reject(Long adminId, Long applicationId, String reason);

  record ApplicationDetails(ProviderApplication application, Account applicant) {}
}
