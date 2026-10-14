package com.renttool.infrastructure.config;

import com.renttool.application.identity.port.in.GetProfileUseCase;
import com.renttool.infrastructure.identity.security.ActiveAccountFilter;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

/** Reglas de acceso HTTP de toda la aplicación. Se ensamblan aquí porque abarcan varios módulos. */
@Configuration
public class SecurityConfig {
  @Bean
  SecurityFilterChain security(
      HttpSecurity http, GetProfileUseCase profiles, JwtAuthenticationConverter converter)
      throws Exception {
    http.cors(Customizer.withDefaults())
        .csrf(c -> c.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers("/api/v1/auth/**")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.GET, "/api/v1/tools", "/api/v1/tools/{id}", "/api/v1/categories")
                    .permitAll()
                    .requestMatchers("/api/**")
                    .authenticated()
                    .anyRequest()
                    .permitAll())
        .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(converter)))
        .addFilterAfter(new ActiveAccountFilter(profiles), BearerTokenAuthenticationFilter.class);
    return http.build();
  }
}
