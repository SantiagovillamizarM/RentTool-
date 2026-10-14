package com.renttool.application.identity.port.in;

import com.renttool.domain.identity.Account;

public interface UpdateProfileUseCase {
  Account rename(Long accountId, String name);
}
