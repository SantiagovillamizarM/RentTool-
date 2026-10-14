package com.renttool.infrastructure.identity.web;

import com.renttool.application.identity.port.in.*;
import com.renttool.infrastructure.identity.web.IdentityDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AuthController {
  private final RegisterAccountUseCase register;
  private final LoginUseCase login;
  private final GetProfileUseCase getProfile;
  private final UpdateProfileUseCase updateProfile;

  public AuthController(
      RegisterAccountUseCase register,
      LoginUseCase login,
      GetProfileUseCase getProfile,
      UpdateProfileUseCase updateProfile) {
    this.register = register;
    this.login = login;
    this.getProfile = getProfile;
    this.updateProfile = updateProfile;
  }

  @PostMapping("/auth/register")
  @ResponseStatus(HttpStatus.CREATED)
  public SessionResponse register(@Valid @RequestBody RegistrationRequest r) {
    return SessionResponse.from(
        register.register(
            new RegisterAccountUseCase.Command(r.name(), r.email(), r.password(), r.role())));
  }

  @PostMapping("/auth/login")
  public SessionResponse login(@Valid @RequestBody LoginRequest l) {
    return SessionResponse.from(login.login(l.email(), l.password()));
  }

  @GetMapping("/me")
  public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
    return UserResponse.from(getProfile.getActive(Long.valueOf(jwt.getSubject())));
  }

  @PutMapping("/me")
  public UserResponse update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProfileRequest p) {
    return UserResponse.from(updateProfile.rename(Long.valueOf(jwt.getSubject()), p.name()));
  }
}
