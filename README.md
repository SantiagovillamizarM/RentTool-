# RentTool

Plataforma de alquiler de herramientas y equipos de construcción. Este incremento implementa el primer MVP: identidad, inventario y catálogo, con una interfaz oscura y amarilla basada en la referencia compartida.

**Java 21 · Spring Boot 4.1.1 · MySQL · arquitectura hexagonal y DDD · Maven multimódulo · HTML/CSS/JavaScript**

## Funciones implementadas

- Registro de clientes y proveedores, login JWT y edición del nombre de perfil.
- Restricciones por rol y propietario; el registro público no permite crear administradores.
- Publicación, edición y retirada lógica de herramientas con tarifa diaria y categoría.
- Unidades físicas con códigos únicos y estados operativa, mantenimiento o retirada.
- Imágenes HTTPS o subida PNG/JPEG con límites de tamaño y dimensiones.
- Catálogo paginado, búsqueda literal, filtros por categoría y detalle de herramientas.
- Portada responsiva, panel de inventario, estados vacíos, errores y notificaciones.

Reservas, disponibilidad por fechas, pagos, facturas, entregas, devoluciones y administración global **siguen pendientes** según [la planeación](docs/PLANEACION_PROYECTO.md). El conteo de unidades operativas no implica disponibilidad para un rango de fechas. No se cobran pagos desde esta versión.

## Ejecución con Docker

Requiere Docker Engine activo y Docker Compose.

```bash
cp .env.example .env
# Edita .env: configura contraseñas y un JWT_SECRET aleatorio de al menos 32 caracteres.
docker compose up --build
```

Abre **http://localhost:8088**. El frontend y la API se sirven desde el mismo origen. MySQL y las imágenes persisten en volúmenes. No abras `index.html` con `file://`: necesita la API.

Para cargar seis herramientas de demostración, añade `DEMO_PASSWORD` (mínimo 10 caracteres) a `.env` y ejecuta:

```bash
docker compose -f compose.yaml -f compose.demo.yaml up --build
```

Cuenta demo: `proveedor@renttool.demo`, contraseña definida en `DEMO_PASSWORD`. El perfil demo solo puebla el inventario si está vacío; sus publicaciones se identifican como demostración. No uses este perfil con datos reales.

## Compilación y pruebas

Con JDK 21:

```bash
./mvnw verify
```

Las pruebas automatizadas usan H2 en modo MySQL exclusivamente para pruebas; no sustituyen la validación contra MySQL. La aplicación utiliza MySQL y Flyway en ejecución normal.

- [Compilación](docs/COMPILACION.md)
- [Despliegue](docs/DESPLIEGUE.md)
- [Modelo de datos](docs/MODELO_DATOS.md)
- [API del MVP](docs/API_MVP1.md)
- [Planeación por MVP](docs/PLANEACION_PROYECTO.md)
- [Contexto y archivos conservados](docs/MEMORIA_PROYECTO.md)
- [Especificación completa](docs/DESGLOSE_PROYECTO_IA.md)

## Estructura

Arquitectura hexagonal (puertos y adaptadores) por módulo; detalle en [docs/ARQUITECTURA.md](docs/ARQUITECTURA.md).

Tres módulos Maven, uno por capa, cada uno con un paquete por contexto (`identity`, `inventory`):

- `domain/`: agregados y reglas de negocio en Java puro, sin dependencias.
- `application/`: casos de uso (`port/in`), puertos de salida (`port/out`) y servicios; solo depende de `domain`.
- `infrastructure/`: adaptadores web, JPA, JWT y archivos, configuración, migraciones y arranque de Spring Boot. Genera el JAR ejecutable.
- `frontend/`: fuentes estáticas incluidas automáticamente en el JAR por Maven.

El JWT se conserva en `sessionStorage`, con vencimiento de una hora. Cerrar sesión lo elimina de este navegador; no revoca una copia del token. El backend revisa que la cuenta siga activa en cada petición autenticada. El uso de un header Bearer permite desactivar CSRF sin usar cookies de autenticación. CORS se limita a orígenes configurados. Las imágenes externas no envían referrer desde la interfaz.

## Límites actuales

Swagger/OpenAPI interactivo, auditoría, perfiles comerciales completos, paginación del panel de inventario más allá de 100 elementos y el resto de MVP aún deben incorporarse. Las imágenes eliminadas de una publicación conservan su archivo físico; su limpieza se implementará con una política de retención. No se inventan garantías comerciales, estadísticas ni contacto de una empresa real.

Para provisionar el administrador inicial en ejecución JAR, configura `RENTTOOL_ADMIN_EMAIL` y `RENTTOOL_ADMIN_PASSWORD` (12–64 caracteres). No se eleva una cuenta existente ni se cambia su contraseña automáticamente. El panel administrativo global pertenece a un MVP posterior.
