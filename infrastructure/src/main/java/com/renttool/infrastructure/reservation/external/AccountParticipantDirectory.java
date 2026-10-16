package com.renttool.infrastructure.reservation.external;

import com.renttool.application.identity.port.out.AccountRepository;
import com.renttool.application.identity.port.out.ProviderProfileRepository;
import com.renttool.application.reservation.port.out.ParticipantDirectory;
import com.renttool.domain.identity.Account;
import org.springframework.stereotype.Component;

/** Obtiene nombre, correo y NIT desde las cuentas y perfiles de identidad. */
@Component
class AccountParticipantDirectory implements ParticipantDirectory {
  private final AccountRepository accounts;
  private final ProviderProfileRepository profiles;

  AccountParticipantDirectory(AccountRepository accounts, ProviderProfileRepository profiles) {
    this.accounts = accounts;
    this.profiles = profiles;
  }

  @Override
  public Card cardOf(Long participantId) {
    return accounts
        .findById(participantId)
        .map(
            a ->
                new Card(
                    a.name(),
                    a.email(),
                    a.isProvider()
                        ? profiles
                            .findByAccountId(a.id())
                            .map(p -> p.nit().toString())
                            .orElse(null)
                        : null))
        .orElse(new Card("Usuario " + participantId, null, null));
  }

  @Override
  public String nameOf(Long participantId) {
    return accounts.findById(participantId).map(Account::name).orElse("Usuario " + participantId);
  }
}
