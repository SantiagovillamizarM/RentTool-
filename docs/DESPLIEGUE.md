# Despliegue local

1. Activa Docker Engine con acceso desde tu usuario.
2. Copia `.env.example` a `.env` y configura secretos. El archivo `.env` está excluido de Git y del contexto Docker.
3. Ejecuta `docker compose up --build`.
4. Abre `http://localhost:8088` y crea una cuenta de proveedor para publicar tu primer equipo.
5. Para detener: `docker compose down`. No añadas `-v` si quieres conservar los datos.

Servicios: MySQL 8.4, aplicación Java 21 con Tomcat embebido y Boot 4.1.1. La aplicación se enlaza a loopback (APP_PORT=8088 por defecto); MySQL solo se expone dentro de la red de Compose. MySQL usa `mysql_data`; imágenes usan `uploads`. El contenedor de aplicación se ejecuta con UID 10001.

Variables obligatorias: `DB_PASSWORD`, `MYSQL_ROOT_PASSWORD`, `JWT_SECRET`. Opcionales en ejecución JAR: `DB_URL`, `DB_USER`, `SERVER_PORT`, `RENTTOOL_UPLOAD_DIR`, `RENTTOOL_CORS_ORIGINS`. Administrador inicial opcional en JAR: `RENTTOOL_ADMIN_EMAIL` y `RENTTOOL_ADMIN_PASSWORD`; no hay credenciales administrativas predeterminadas. El secreto JWT debe tener al menos 32 caracteres. La ejecución demo requiere además `DEMO_PASSWORD` y `SPRING_PROFILES_ACTIVE=demo`.

Respaldar MySQL con las herramientas de su versión y respaldar el volumen de imágenes; verificar restauraciones en una instancia separada. No se configura aquí un dominio público ni HTTPS. Para un despliegue público se debe añadir TLS y una política operativa apropiada.

El despliegue usa JAR, no requiere Tomcat externo. Si se añade WAR, seleccionar un contenedor compatible con Boot 4.1.1 y documentar esa nueva modalidad.
