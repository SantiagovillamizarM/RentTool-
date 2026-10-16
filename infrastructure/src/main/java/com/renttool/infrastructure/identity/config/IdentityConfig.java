package com.renttool.infrastructure.identity.config;

import com.renttool.application.identity.port.out.*;
import com.renttool.application.identity.service.*;
import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.transaction.PlatformTransactionManager;

/** Ensambla los casos de uso de identidad con sus adaptadores de salida. */
@Configuration
public class IdentityConfig {
  @Bean
  AuthenticationService authenticationService(
      AccountRepository accounts, PasswordHasher passwords, TokenIssuer tokens, Clock clock) {
    return new AuthenticationService(accounts, passwords, tokens, clock);
  }

  @Bean
  ProfileService profileService(
      AccountRepository accounts, ProviderProfileRepository profiles) {
    return new ProfileService(accounts, profiles);
  }

  @Bean
  AccountProvisioningService accountProvisioningService(
      AccountRepository accounts, PasswordHasher passwords) {
    return new AccountProvisioningService(accounts, passwords);
  }

  @Bean
  TransactionalProviders providerUseCases(
      AccountRepository accounts,
      ProviderProfileRepository profiles,
      PasswordHasher passwords,
      LogoStorage logos,
      PlatformTransactionManager transactions) {
    return new TransactionalProviders(
        new ProviderAdministrationService(accounts, profiles, passwords, logos), transactions);
  }
}
