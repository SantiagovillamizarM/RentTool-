package com.renttool.infrastructure.identity.security;

import com.renttool.application.identity.port.in.GetProfileUseCase;
import com.renttool.domain.identity.Account;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rechaza con 401 un token válido cuya cuenta ya no está activa o cambió de rol (un cliente
 * aceptado como proveedor), y con 403 cualquier operación de una cuenta con contraseña temporal
 * salvo ver el perfil y cambiarla. Ante un cambio de rol solo se admiten ver el perfil y renovar el
 * token, que sale con el rol nuevo. No se registra como bean para que el contenedor no lo añada dos
 * veces: se instancia en la cadena de seguridad.
 */
public class ActiveAccountFilter extends OncePerRequestFilter {
  private static final String PROFILE = "/api/v1/me";
  private static final String PASSWORD = "/api/v1/me/password";
  private static final String REFRESH = "/api/v1/auth/refresh";
  private static final String PASSWORD_REQUIRED =
      "{\"type\":\"about:blank\",\"title\":\"Forbidden\",\"status\":403,"
          + "\"code\":\"PASSWORD_CHANGE_REQUIRED\","
          + "\"detail\":\"Debes cambiar la contraseña temporal antes de continuar\"}";
  private static final String ROLE_CHANGED =
      "{\"type\":\"about:blank\",\"title\":\"Unauthorized\",\"status\":401,"
          + "\"code\":\"ROLE_CHANGED\","
          + "\"detail\":\"Tu cuenta cambió de rol; renueva la sesión para continuar\"}";

  private final GetProfileUseCase profiles;

  public ActiveAccountFilter(GetProfileUseCase profiles) {
    this.profiles = profiles;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken jwt) {
      Account account;
      try {
        account = profiles.getActive(Long.valueOf(jwt.getToken().getSubject()));
      } catch (RuntimeException e) {
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        return;
      }
      String tokenRole = jwt.getToken().getClaimAsString(JwtTokenIssuer.ROLE_CLAIM);
      if (!account.role().name().equals(tokenRole) && !allowedWithStaleRole(request)) {
        write(response, HttpServletResponse.SC_UNAUTHORIZED, ROLE_CHANGED);
        return;
      }
      if (account.mustChangePassword() && !allowedWithTemporaryPassword(request)) {
        write(response, HttpServletResponse.SC_FORBIDDEN, PASSWORD_REQUIRED);
        return;
      }
    }
    chain.doFilter(request, response);
  }

  private static void write(HttpServletResponse response, int status, String body)
      throws IOException {
    response.setStatus(status);
    response.setContentType("application/problem+json");
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.getWriter().write(body);
  }

  private static String path(HttpServletRequest request) {
    return request.getRequestURI().substring(request.getContextPath().length());
  }

  private static boolean allowedWithStaleRole(HttpServletRequest request) {
    String path = path(request);
    return ("GET".equals(request.getMethod()) && PROFILE.equals(path))
        || ("POST".equals(request.getMethod()) && REFRESH.equals(path))
        || !path.startsWith("/api/");
  }

  private static boolean allowedWithTemporaryPassword(HttpServletRequest request) {
    String path = path(request);
    return ("GET".equals(request.getMethod()) && PROFILE.equals(path))
        || ("PUT".equals(request.getMethod()) && PASSWORD.equals(path))
        || !path.startsWith("/api/");
  }
}
