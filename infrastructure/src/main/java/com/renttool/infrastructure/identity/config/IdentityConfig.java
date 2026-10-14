package com.renttool.infrastructure.identity.config;

import com.renttool.application.identity.port.out.*;
import com.renttool.application.identity.service.*;
import org.springframework.context.annotation.*;

/** Ensambla los casos de uso de identidad con sus adaptadores de salida. */
@Configuration
public class IdentityConfig {
  @Bean
  AuthenticationService authenticationService(
      AccountRepository accounts, PasswordHasher passwords, TokenIssuer tokens) {
    return new AuthenticationService(accounts, passwords, tokens);
  }

  @Bean
  ProfileService profileService(AccountRepository accounts) {
    return new ProfileService(accounts);
  }

  @Bean
  AccountProvisioningService accountProvisioningService(
      AccountRepository accounts, PasswordHasher passwords) {
    return new AccountProvisioningService(accounts, passwords);
  }
}
