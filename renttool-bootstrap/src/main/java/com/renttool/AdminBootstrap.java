package com.renttool;

import com.renttool.identity.application.AccountRepository;
import com.renttool.identity.domain.Account;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@ConditionalOnProperty(name = "renttool.admin-email")
public class AdminBootstrap {
  @Bean
  CommandLineRunner createAdmin(
      AccountRepository accounts,
      PasswordEncoder passwords,
      @Value("${renttool.admin-email}") String email,
      @Value("${renttool.admin-password}") String password) {
    return args -> {
      if (password.length() < 12 || password.length() > 64)
        throw new IllegalArgumentException(
            "La contraseña administrativa requiere entre 12 y 64 caracteres");
      String normalized = email.strip().toLowerCase(Locale.ROOT);
      if (!normalized.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+"))
        throw new IllegalArgumentException("Correo administrativo inválido");
      var existing = accounts.findByEmail(normalized);
      if (existing.isPresent()) {
        if (existing.get().role() != Account.Role.ADMINISTRADOR)
          throw new IllegalStateException(
              "El correo configurado ya pertenece a una cuenta no administrativa");
        return;
      }
      accounts.save(
          new Account(
              null,
              "Administrador",
              normalized,
              passwords.encode(password),
              Account.Role.ADMINISTRADOR,
              true));
    };
  }
}
