package com.renttool.infrastructure.reservation.external;

import com.renttool.application.identity.port.out.AccountRepository;
import com.renttool.application.reservation.port.out.ParticipantDirectory;
import com.renttool.domain.identity.Account;
import org.springframework.stereotype.Component;

/** Obtiene el nombre visible desde las cuentas de identidad. */
@Component
class AccountParticipantDirectory implements ParticipantDirectory {
  private final AccountRepository accounts;

  AccountParticipantDirectory(AccountRepository accounts) {
    this.accounts = accounts;
  }

  @Override
  public String nameOf(Long participantId) {
    return accounts.findById(participantId).map(Account::name).orElse("Usuario " + participantId);
  }
}
