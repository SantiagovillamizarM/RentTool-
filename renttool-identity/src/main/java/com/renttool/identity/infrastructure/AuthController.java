package com.renttool.identity.infrastructure;

import com.renttool.identity.application.*;
import com.renttool.identity.domain.Account;
import com.renttool.shared.BusinessException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AuthController {
  private final AccountService service;
  private final AccountRepository repository;
  private final PasswordEncoder passwords;
  private final JwtEncoder encoder;

  public AuthController(AccountService s, AccountRepository r, PasswordEncoder p, JwtEncoder e) {
    service = s;
    repository = r;
    passwords = p;
    encoder = e;
  }

  public record Registration(
      @NotBlank @Size(max = 120) String name,
      @NotBlank @Email @Size(max = 190) String email,
      @NotBlank @Size(min = 10, max = 64) String password,
      @NotNull Account.Role role) {}

  public record Login(@NotBlank @Email String email, @NotBlank String password) {}

  public record Profile(@NotBlank @Size(max = 120) String name) {}

  public record User(Long id, String name, String email, Account.Role role) {}

  private User user(Account a) {
    return new User(a.id(), a.name(), a.email(), a.role());
  }

  private Map<String, Object> token(Account a) {
    var now = Instant.now();
    var claims =
        JwtClaimsSet.builder()
            .issuer("renttool")
            .subject(a.id().toString())
            .issuedAt(now)
            .expiresAt(now.plusSeconds(3600))
            .claim("role", a.role().name())
            .build();
    return Map.of(
        "token",
        encoder
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue(),
        "user",
        user(a));
  }

  @PostMapping("/auth/register")
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public Map<String, Object> register(@Valid @RequestBody Registration r) {
    return token(service.register(r.name(), r.email(), passwords.encode(r.password()), r.role()));
  }

  @PostMapping("/auth/login")
  public Map<String, Object> login(@Valid @RequestBody Login l) {
    var a =
        repository
            .findByEmail(l.email().strip().toLowerCase(Locale.ROOT))
            .filter(Account::active)
            .filter(x -> passwords.matches(l.password(), x.passwordHash()))
            .orElseThrow(() -> new BusinessException(401, "Correo o contraseña incorrectos"));
    return token(a);
  }

  @GetMapping("/me")
  public User me(@AuthenticationPrincipal Jwt jwt) {
    return user(service.get(Long.valueOf(jwt.getSubject())));
  }

  @PutMapping("/me")
  public User update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Profile p) {
    return user(service.update(Long.valueOf(jwt.getSubject()), p.name()));
  }
}
