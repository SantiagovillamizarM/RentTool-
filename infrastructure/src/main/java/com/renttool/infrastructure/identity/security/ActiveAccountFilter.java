package com.renttool.infrastructure.identity.security;

import com.renttool.application.identity.port.in.GetProfileUseCase;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rechaza con 401 un token válido cuya cuenta ya no está activa, y con 403 cualquier operación de
 * una cuenta con contraseña temporal salvo ver el perfil y cambiarla. No se registra como bean
 * para que el contenedor no lo añada dos veces: se instancia en la cadena de seguridad.
 */
public class ActiveAccountFilter extends OncePerRequestFilter {
  private static final String PROFILE = "/api/v1/me";
  private static final String PASSWORD = "/api/v1/me/password";
  private static final String PASSWORD_REQUIRED =
      "{\"type\":\"about:blank\",\"title\":\"Forbidden\",\"status\":403,"
          + "\"code\":\"PASSWORD_CHANGE_REQUIRED\","
          + "\"detail\":\"Debes cambiar la contraseña temporal antes de continuar\"}";

  private final GetProfileUseCase profiles;

  public ActiveAccountFilter(GetProfileUseCase profiles) {
    this.profiles = profiles;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken jwt) {
      boolean mustChangePassword;
      try {
        mustChangePassword =
            profiles.getActive(Long.valueOf(jwt.getToken().getSubject())).mustChangePassword();
      } catch (RuntimeException e) {
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        return;
      }
      if (mustChangePassword && !allowedWithTemporaryPassword(request)) {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(PASSWORD_REQUIRED);
        return;
      }
    }
    chain.doFilter(request, response);
  }

  private static boolean allowedWithTemporaryPassword(HttpServletRequest request) {
    String path = request.getRequestURI().substring(request.getContextPath().length());
    return ("GET".equals(request.getMethod()) && PROFILE.equals(path))
        || ("PUT".equals(request.getMethod()) && PASSWORD.equals(path))
        || !path.startsWith("/api/");
  }
}
