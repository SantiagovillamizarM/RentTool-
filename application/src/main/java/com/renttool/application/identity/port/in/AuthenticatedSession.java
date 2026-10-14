package com.renttool.application.identity.port.in;

import com.renttool.domain.identity.Account;

public record AuthenticatedSession(Account account, String token) {}
