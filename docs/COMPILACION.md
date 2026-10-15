# Compilación

Requisitos: JDK 21 y conexión a Maven Central la primera vez. Wrapper fija Maven 3.9.12; POM fija Boot 4.1.1 y `release=21`.

```bash
java -version
./mvnw verify
```

El JAR está en `infrastructure/target/renttool-infrastructure-0.1.0-SNAPSHOT.jar`. Maven copia `frontend/` al directorio estático del JAR; no se necesitan Node ni un bundler para ejecutar el producto. Para verificar sintaxis de JavaScript opcionalmente: `node --check frontend/app.js`.

Las pruebas de API comprueban registro, exclusión de administradores públicos, 401, permisos, propiedad, unidades, catálogo, retirada y validación. Usan una base H2 efímera solo para pruebas. Las comprobaciones contra MySQL se registran aparte en VALIDACION.md.

Para ejecutar con MySQL existente, crea una base vacía y un usuario con permisos sobre ella, configura las variables y arranca el JAR:

```bash
export DB_URL=jdbc:mysql://localhost:3306/renttool
export SERVER_PORT=8088
export DB_USER=renttool
export DB_PASSWORD='tu-contraseña-local'
export JWT_SECRET='tu-secreto-aleatorio-de-al-menos-32-caracteres'
java -jar infrastructure/target/renttool-infrastructure-0.1.0-SNAPSHOT.jar
```

Estos son ejemplos de configuración; usa valores propios fuera del repositorio. Flyway crea el esquema y Hibernate valida sus tipos. Para editar frontend y ver cambios, vuelve a empaquetar el JAR. La API de reservas está disponible; pagos y facturas aún no existen.
