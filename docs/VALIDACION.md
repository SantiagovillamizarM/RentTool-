# Validación del primer MVP

Fecha: 13 de octubre de 2026, America/Bogota.

## Resultado

- Java 21 y Spring Boot 4.1.1: compilación y JAR ejecutable correctos.
- `mvn verify`: siete pruebas sin fallos contra MySQL 8.4, con Flyway, MySQL Connector/J e Hibernate validando el esquema. Se usaron bases temporales independientes de las bases existentes del equipo.
- Cinco pruebas de API: registro y seguridad; catálogo/propiedad/baja; validación y credenciales; imágenes y acceso a unidades; cuenta desactivada con token previo.
- Dos pruebas de dominio: tarifa positiva y prohibición de transferir propiedad al editar.
- Pruebas con H2: cinco casos de API pasaron antes de añadir los dos casos de dominio; H2 se utiliza solo como alternativa efímera en pruebas locales.
- `node --check frontend/app.js` y `node --check scripts/check-browser.mjs`: sintaxis válida.
- Navegador Chrome real contra la demo MySQL: seis publicaciones, menú móvil, registro proveedor, publicación, edición, unidad, subida de PNG, retirada y cierre de sesión. Sin excepciones JavaScript y sin desbordamiento horizontal a 390 px, incluida sesión iniciada y acceso al cierre de sesión.
- Capturas: `screenshots/desktop.png`, `screenshots/mobile.png` y `screenshots/provider.png`.

## Reproducir pruebas MySQL

Usa una base **vacía dedicada a pruebas**. Los tests crean usuarios y herramientas y no deben apuntar a datos reales. Configura `TEST_DB_URL`, `TEST_DB_DRIVER=com.mysql.cj.jdbc.Driver`, `TEST_DB_USER` y `TEST_DB_PASSWORD` y ejecuta `./mvnw verify`. Una segunda ejecución requiere otra base vacía porque los tests verifican correos únicos con datos fijos.

Para la prueba de navegador, arranca la aplicación con perfil demo en una base independiente, inicia Chrome con un perfil temporal y depuración local en el puerto 9228 y ejecuta `node scripts/check-browser.mjs`. `RENTTOOL_TEST_URL` permite cambiar la URL por defecto `http://127.0.0.1:8088`; `CHROME_DEBUG_URL` permite cambiar el endpoint de depuración. La prueba espera seis publicaciones demo y crea un usuario y una publicación que retira al terminar. No usa una base de producción.

## Límites de esta verificación

Docker Compose se preparó, pero no se ejecutó: Docker Desktop no estaba disponible y el usuario no tenía acceso al socket del motor del sistema. La validación real usó JAR y una instancia aislada de MySQL. No se validaron pagos ni reservas porque aún no están implementados.

El procedimiento administrativo inicial es opcional y se configura mediante variables; la batería actual no incluye su prueba específica. La prueba móvil comprueba viewport y navegación, sin emular un dispositivo físico. Las capturas muestran datos de demostración y una publicación temporal de QA.
