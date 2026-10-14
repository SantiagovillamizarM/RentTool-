package com.renttool.application.identity.port.out;

import com.renttool.domain.identity.Account;

public interface TokenIssuer {
  String issue(Account account);
}
