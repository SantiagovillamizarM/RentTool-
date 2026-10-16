package com.renttool.infrastructure.config;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class WebConfig implements WebMvcConfigurer {
  @Value("${renttool.cors-origins:http://localhost:8080}")
  private String[] origins;

  @Value("${renttool.upload-dir:uploads}")
  private String uploadDir;

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry r) {
    r.addResourceHandler("/uploads/**")
        .addResourceLocations(Path.of(uploadDir).toAbsolutePath().toUri().toString() + "/");
  }

  /** El panel de administración es una página aparte; su acceso lo valida la API, no la ruta. */
  @Override
  public void addViewControllers(ViewControllerRegistry r) {
    r.addViewController("/admin").setViewName("forward:/admin.html");
  }

  @Override
  public void addCorsMappings(CorsRegistry r) {
    r.addMapping("/api/**")
        .allowedOrigins(origins)
        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
        .allowedHeaders("Authorization", "Content-Type")
        .maxAge(3600);
  }
}
