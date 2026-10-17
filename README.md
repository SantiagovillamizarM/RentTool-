# RentTool

Plataforma de alquiler de herramientas y equipos de construcción. Este incremento implementa el primer MVP: identidad, inventario y catálogo, con una interfaz oscura y amarilla basada en la referencia compartida.

**Java 21 · Spring Boot 4.1.1 · MySQL · arquitectura hexagonal y DDD · Maven multimódulo · HTML/CSS/JavaScript**

## Funciones implementadas

- Registro público de clientes, login JWT, edición del nombre y cambio de contraseña.
- **Alta de proveedores por el administrador**: correo, contraseña temporal, tipo de persona, NIT con dígito de verificación DIAN, razón social, nombre comercial, representante legal, teléfono, dirección y ciudad. El proveedor debe cambiar la contraseña temporal antes de usar la API; el administrador puede editar sus datos, desactivarlo (pierde el acceso al instante) o asignarle una nueva clave temporal.
- Restricciones por rol y propietario; el registro público solo crea clientes.
- **"¿Quieres ser proveedor?"**: un cliente envía los datos de su empresa desde el sitio; el administrador acepta o rechaza con motivo. La próxima vez que entra ve la respuesta una sola vez: el motivo del rechazo (con opción de reenviar) o la bienvenida con su nombre comercial y el acceso a su panel.
- **Panel del proveedor** en `/proveedor`, aparte del sitio: inventario (publicar, editar con imagen principal, unidades e imágenes, retirar), solicitudes de reserva y sus datos legales de solo lectura.
- **Editar herramientas desde el panel del administrador**: nombre, categoría, descripción, tarifa e imagen principal (cambiarla o quitarla). Sin imagen se muestra una ilustración predeterminada. Las reservas ya solicitadas conservan la tarifa pactada.
- **Eliminar proveedor** desde el panel, con aviso previo de lo que pasará y confirmación escribiendo su nombre: cierra su cuenta para siempre, retira todas sus herramientas del catálogo y cancela sus reservas que aún no empiezan (los alquileres en curso siguen). Sus datos se conservan para el historial.
- **Historial y auditoría de reservas**: cada solicitud, aceptación, rechazo, cancelación y vencimiento queda registrado con quién lo hizo y cuándo, visible en "Historial" de cada reserva.
- **Informes descargables (CSV para Excel)** desde el panel del administrador: reservas por rango de fechas y estado, con cliente y correo, proveedor y NIT, importes y último cambio; o el historial completo de eventos. Cada descarga queda registrada.
- Publicación, edición y retirada lógica de herramientas con tarifa diaria y categoría.
- Unidades físicas con códigos únicos y estados operativa, mantenimiento o retirada.
- Imágenes HTTPS o subida PNG/JPEG con límites de tamaño y dimensiones.
- Catálogo paginado, búsqueda literal, filtros por categoría y detalle de herramientas.
- Portada responsiva, panel de inventario, estados vacíos, errores y notificaciones.
- **Reservas por fechas**: disponibilidad pública por rango, solicitud del cliente con tarifa congelada, aceptación del proveedor con asignación de unidades bajo bloqueo, rechazo con motivo, cancelación y vencimiento automático de solicitudes sin respuesta.

Pagos, facturas, entregas, devoluciones y el resto de la administración global (usuarios, incidencias, reportes) **siguen pendientes** según [la planeación](docs/PLANEACION_PROYECTO.md). No se cobran pagos desde esta versión.

## Ejecución con Docker

Requiere Docker Engine activo y Docker Compose.

```bash
cp .env.example .env
# Edita .env: configura contraseñas, un JWT_SECRET aleatorio de al menos 32 caracteres
# y el administrador inicial (RENTTOOL_ADMIN_EMAIL y RENTTOOL_ADMIN_PASSWORD).
docker compose up --build
```

Abre **http://localhost:8088**. El frontend y la API se sirven desde el mismo origen. Los proveedores trabajan en **http://localhost:8088/proveedor**. Al iniciar sesión como administrador se abre el panel **http://localhost:8088/admin**, separado del sitio público: proveedores, moderación del catálogo y todas las reservas. Desde ahí, "Ver sitio público" (o "Ver cambios" tras retirar una herramienta) lleva al sitio con una barra superior para volver al panel.

La documentación interactiva de la API (Swagger) está en **http://localhost:8088/swagger-ui.html**. Para probar los endpoints protegidos, obtén un token con `/auth/login`, pulsa **Authorize** y pégalo. MySQL y las imágenes persisten en volúmenes. No abras `index.html` con `file://`: necesita la API.

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

Tres módulos Maven, uno por capa, cada uno con un paquete por contexto (`identity`, `inventory`, `reservation`):

- `domain/`: agregados y reglas de negocio en Java puro, sin dependencias.
- `application/`: casos de uso (`port/in`), puertos de salida (`port/out`) y servicios; solo depende de `domain`.
- `infrastructure/`: adaptadores web, JPA, JWT y archivos, configuración, migraciones y arranque de Spring Boot. Genera el JAR ejecutable.
- `frontend/`: fuentes estáticas incluidas automáticamente en el JAR por Maven.

El JWT se conserva en `sessionStorage`, con vencimiento de una hora. El del administrador vence a los **10 minutos** y la interfaz lo renueva (`/auth/refresh`) solo mientras hay actividad; tras 10 minutos sin actividad cierra la sesión sin preguntar y pide iniciar sesión de nuevo, y aunque alguien copiara el token, el servidor ya no lo acepta. Ninguna sesión se renueva más de 8 horas después del inicio de sesión. Cerrar sesión lo elimina de este navegador; no revoca una copia del token. El backend revisa que la cuenta siga activa en cada petición autenticada. El uso de un header Bearer permite desactivar CSRF sin usar cookies de autenticación. CORS se limita a orígenes configurados. Las imágenes externas no envían referrer desde la interfaz.

## Límites actuales

Auditoría, perfiles comerciales completos, paginación del panel de inventario más allá de 100 elementos y el resto de MVP aún deben incorporarse. Las imágenes eliminadas de una publicación conservan su archivo físico; su limpieza se implementará con una política de retención. No se inventan garantías comerciales, estadísticas ni contacto de una empresa real.

El administrador inicial se provisiona al arrancar con `RENTTOOL_ADMIN_EMAIL` y `RENTTOOL_ADMIN_PASSWORD` (12–64 caracteres); Docker Compose las exige. Sin él nadie puede dar de alta proveedores. No se eleva una cuenta existente ni se cambia su contraseña automáticamente. Los proveedores creados por registro público antes de este cambio aparecen en el panel como "Sin datos legales" para que el administrador los complete o desactive; sus publicaciones siguen visibles hasta que se retiren.
