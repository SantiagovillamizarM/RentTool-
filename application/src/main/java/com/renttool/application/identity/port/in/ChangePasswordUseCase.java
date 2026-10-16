package com.renttool.application.identity.port.in;

import com.renttool.domain.identity.Account;

public interface ChangePasswordUseCase {
  /** Exige la contraseña actual y libera la cuenta si tenía una temporal. */
  Account changePassword(Long accountId, String currentPassword, String newPassword);
}
