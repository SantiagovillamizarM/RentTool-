# Validación del primer MVP

Este documento conserva resultados históricos de versiones anteriores; no es una guía de pruebas de la versión actual. El antiguo script de navegador se retiró porque esperaba seis publicaciones demo, registro público de proveedores y formularios que ya no existen. Para ejecutar las comprobaciones actuales, consulta la sección de pruebas del [README](../README.md).

Fecha: 13 de octubre de 2026, America/Bogota.

## Resultado

- Java 21 y Spring Boot 4.1.1: compilación y JAR ejecutable correctos.
- `mvn verify`: siete pruebas sin fallos contra MySQL 8.4, con Flyway, MySQL Connector/J e Hibernate validando el esquema. Se usaron bases temporales independientes de las bases existentes del equipo.
- Cinco pruebas de API: registro y seguridad; catálogo/propiedad/baja; validación y credenciales; imágenes y acceso a unidades; cuenta desactivada con token previo.
- Dos pruebas de dominio: tarifa positiva y prohibición de transferir propiedad al editar.
- Pruebas con H2: cinco casos de API pasaron antes de añadir los dos casos de dominio; H2 se utiliza solo como alternativa efímera en pruebas locales.
- La sintaxis de frontend/app.js y del antiguo script de navegador se comprobó en esa versión.
- Navegador Chrome real contra la demo MySQL: seis publicaciones, menú móvil, registro proveedor, publicación, edición, unidad, subida de PNG, retirada y cierre de sesión. Sin excepciones JavaScript y sin desbordamiento horizontal a 390 px, incluida sesión iniciada y acceso al cierre de sesión.
- Capturas: `screenshots/desktop.png`, `screenshots/mobile.png` y `screenshots/provider.png`.

## Reproducir pruebas MySQL

Usa una base **vacía dedicada a pruebas**. Los tests crean usuarios y herramientas y no deben apuntar a datos reales. Configura `TEST_DB_URL`, `TEST_DB_DRIVER=com.mysql.cj.jdbc.Driver`, `TEST_DB_USER` y `TEST_DB_PASSWORD` y ejecuta `./mvnw verify`. Una segunda ejecución requiere otra base vacía porque los tests verifican correos únicos con datos fijos.

La prueba de navegador descrita en este registro se ejecutó con la interfaz antigua. Ese procedimiento ya no aplica a la versión actual.

## Límites de esta verificación

Docker Compose se preparó, pero no se ejecutó: Docker Desktop no estaba disponible y el usuario no tenía acceso al socket del motor del sistema. La validación real usó JAR y una instancia aislada de MySQL. No se validaron pagos ni reservas porque aún no están implementados.

El procedimiento administrativo inicial es opcional y se configura mediante variables; la batería actual no incluye su prueba específica. La prueba móvil comprueba viewport y navegación, sin emular un dispositivo físico. Las capturas muestran datos de demostración y una publicación temporal de QA.

# Validación de reservas (MVP 02)

Fecha: 15 de octubre de 2026, America/Bogota.

- `./mvnw verify` contra MySQL 8.4 en tres bases vacías distintas: 37 pruebas sin fallos en las tres corridas. Con H2: las mismas 37 pruebas sin fallos.
- Dominio (8): bordes de fechas `[inicio, fin)`, límite de 90 días, total congelado, transiciones de estado, cancelación antes del inicio, vencimiento y permisos por propietario.
- Aplicación (6): disponibilidad con unidades operativas y ocupadas, 409 sin unidades, aceptación que vuelve a comprobar y asigna unidades distintas, decisión solo del proveedor dueño, listado por rol y vencimiento con reloj fijo.
- API (6): disponibilidad pública y validada, flujo solicitar → aceptar → cancelar con 401/403/404/409, rechazo con motivo, dos aceptaciones simultáneas por la última unidad (una 200, otra 409), dos aceptaciones simultáneas de la misma solicitud (se aplica una vez) y documentación Swagger.
- La prueba concurrente se comprobó también en sentido inverso: al quitar el bloqueo de la herramienta, ambas aceptaciones devuelven 200 (doble reserva) en 3 de 3 intentos.
- Hallazgo corregido: en MySQL, con su aislamiento por defecto REPEATABLE READ, la transacción que esperaba el bloqueo seguía leyendo la foto previa y asignaba la misma unidad; H2 no lo reproducía. Las escrituras de reservas usan READ COMMITTED y bloquean primero la reserva y luego la herramienta.
- Migración `V2__reservations.sql` aplicada por Flyway sobre MySQL 8.4 vacío y validada por Hibernate.
- Navegador Chrome real contra la demo MySQL: el script de navegador de esa versión completo sin fallos; recorrido adicional de cliente que cotiza, ve el aviso de unidades insuficientes y solicita, y de proveedor que acepta desde su panel. Sin excepciones JavaScript y sin desbordamiento horizontal a 390 px.
- No se validaron pagos, entregas ni devoluciones porque aún no están implementados.
