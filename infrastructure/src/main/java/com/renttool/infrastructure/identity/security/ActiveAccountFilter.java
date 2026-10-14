package com.renttool.infrastructure.identity.security;

import com.renttool.application.identity.port.in.GetProfileUseCase;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rechaza con 401 un token válido cuya cuenta ya no está activa. No se registra como bean para
 * que el contenedor no lo añada dos veces: se instancia en la cadena de seguridad.
 */
public class ActiveAccountFilter extends OncePerRequestFilter {
  private final GetProfileUseCase profiles;

  public ActiveAccountFilter(GetProfileUseCase profiles) {
    this.profiles = profiles;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken jwt) {
      try {
        profiles.getActive(Long.valueOf(jwt.getToken().getSubject()));
      } catch (RuntimeException e) {
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        return;
      }
    }
    chain.doFilter(request, response);
  }
}
