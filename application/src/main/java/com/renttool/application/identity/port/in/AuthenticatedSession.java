package com.renttool.application.identity.port.in;

import com.renttool.domain.identity.Account;
import java.time.Instant;

/** Token emitido y el instante en que deja de valer. */
public record AuthenticatedSession(Account account, String token, Instant expiresAt) {}
