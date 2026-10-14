package com.renttool.infrastructure.config;

import com.renttool.application.identity.port.in.ProvisionAccountUseCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;

/** Crea el administrador inicial si se configuran RENTTOOL_ADMIN_EMAIL y su contraseña. */
@Configuration
@ConditionalOnProperty(name = "renttool.admin-email")
public class AdminAccountInitializer {
  @Bean
  CommandLineRunner provisionAdministrator(
      ProvisionAccountUseCase accounts,
      @Value("${renttool.admin-email}") String email,
      @Value("${renttool.admin-password}") String password) {
    return args -> accounts.provisionAdministrator(email, password);
  }
}
