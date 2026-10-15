package com.renttool.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.*;

/**
 * Documentación OpenAPI servida en /swagger-ui.html. Los endpoints protegidos declaran el esquema
 * {@value #BEARER} para que Swagger UI envíe el token pegado en "Authorize".
 */
@Configuration
public class OpenApiConfig {
  public static final String BEARER = "bearerAuth";

  private static final String DESCRIPTION =
      """
      API de la plataforma de renta de herramientas y equipos de construcción.

      ## Autenticación con JWT
      1. Crea una cuenta con `POST /api/v1/auth/register` o inicia sesión con \
      `POST /api/v1/auth/login`.
      2. Copia el valor `token` de la respuesta.
      3. Pulsa **Authorize**, pega el token (sin escribir `Bearer`) y confirma.

      Desde ese momento Swagger envía el header `Authorization: Bearer <token>` en los endpoints \
      marcados con candado. El token vence a la hora; si la cuenta se desactiva deja de servir \
      aunque no haya vencido.

      ## Roles
      - **CLIENTE**: explora el catálogo y gestiona su perfil.
      - **PROVEEDOR**: además publica herramientas y gestiona sus unidades e imágenes. Solo \
      puede modificar lo que le pertenece.
      - **ADMINISTRADOR**: no se puede registrar desde la API pública; se provisiona al desplegar.

      ## Errores
      Los errores usan el formato estándar `ProblemDetail` (RFC 9457) con los campos `status`, \
      `title` y `detail`.
      """;

  @Bean
  OpenAPI renttoolOpenApi() {
    return new OpenAPI()
        .info(new Info().title("RentTool API").version("v1").description(DESCRIPTION))
        .components(
            new Components()
                .addSecuritySchemes(
                    BEARER,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Token devuelto por /auth/login o /auth/register")));
  }
}
