# RentTool

RentTool es una plataforma de alquiler de herramientas y equipos de construcción. Permite consultar equipos, reservar unidades por fechas y seguir el alquiler desde la solicitud hasta la entrega, devolución y resolución de incidencias.

El proyecto demuestra el flujo completo de la plataforma. **Los pagos son simulados y las facturas son de demostración: no hay cobros reales ni validez fiscal.**

> **¿Usas Linux?** Ve directo a la [Guía rápida en Linux](#guía-rápida-en-linux): pasos numerados con y sin `sudo`, con y sin datos de prueba.

## Contenido

- [Funcionamiento](#funcionamiento)
- [Tecnologías](#tecnologías)
- [Estructura y arquitectura](#estructura-y-arquitectura)
- [Sistemas compatibles](#sistemas-compatibles)
- [Guía rápida en Linux (con y sin sudo, con y sin datos de prueba)](#guía-rápida-en-linux)
- [Ejecutar con Docker](#ejecutar-con-docker)
- [Ejecutar con Java y MySQL](#ejecutar-con-java-y-mysql)
- [Catálogo de demostración](#catálogo-de-demostración)
- [Probar un alquiler completo](#probar-un-alquiler-completo)
- [Configuración](#configuración)
- [Pruebas y actualización](#pruebas-y-actualización)
- [Solución de problemas](#solución-de-problemas)
- [Limitaciones y documentación](#limitaciones-y-documentación)

## Funcionamiento

El frontend y la API se sirven desde una misma aplicación. No necesitas iniciar otro servidor para la interfaz.

### Roles

| Rol | Funciones |
| --- | --- |
| Visitante | Consultar catálogo, imágenes, tarifas, proveedores y disponibilidad; buscar y ordenar herramientas. |
| Cliente | Registrarse, editar su perfil y teléfono, solicitar y pagar reservas con la pasarela simulada, consultar sus registros y descargar facturas. Puede solicitar convertirse en proveedor. |
| Proveedor | Publicar y editar herramientas, gestionar unidades e imágenes, responder reservas, registrar entregas y devoluciones, gestionar incidencias y consultar estadísticas propias. |
| Administrador | Gestionar proveedores y solicitudes, moderar herramientas, consultar todas las reservas, registrar operaciones, resolver incidencias y consultar estadísticas e informes CSV. |

El registro público crea **clientes**. Para ser proveedor, un cliente envía una solicitud con sus datos comerciales y el administrador la acepta o rechaza con motivo. El administrador también puede darlo de alta directamente con una contraseña temporal, que debe cambiar antes de operar.

El proveedor dispone de un panel propio con inventario, reservas y datos de su empresa. El administrador puede editar datos, desactivar el acceso o gestionar su baja conservando el historial; las operaciones que afecten reservas y pagos están sujetas a las validaciones del sistema.

### Catálogo e inventario

- Búsqueda por nombre o descripción y filtros por categoría y proveedor.
- Orden por publicaciones recientes o tarifa diaria, ascendente o descendente.
- Filtro por fechas de entrega y devolución y cantidad disponible.
- Detalle con descripción, categoría, tarifa e imágenes.
- Cada publicación representa una herramienta; sus unidades representan equipos físicos con código y estado: operativa, mantenimiento o retirada.
- Imágenes por referencia HTTPS o archivos PNG/JPEG, con validaciones de tamaño y dimensiones.

### Recorrido de un alquiler

1. El cliente busca una herramienta e indica fechas y cantidad.
2. Inicia sesión y solicita la reserva. El servidor calcula el importe y conserva la tarifa pactada.
3. El cliente paga con tarjeta o Nequi de prueba en la pasarela simulada. La aprobación genera automáticamente la factura; el rechazo permite reintentar.
4. El proveedor llama al cliente y acepta o rechaza. Solo puede aceptar una reserva pagada; al aceptar, se comprueba nuevamente la disponibilidad y se asignan unidades. Si rechaza, el pago se reembolsa y la factura queda anulada.
5. La entrega se registra automáticamente el día de inicio, a nombre del proveedor. Si una unidad asignada no está lista, se usa otra libre de la misma herramienta; si ninguna lo está, la reserva queda en «Entrega bloqueada» y el proveedor o el administrador pueden cancelarla con reembolso.
6. Al devolver, revisa todas las unidades e indica buen estado, daño o pérdida.
7. RentTool actualiza el inventario y registra incidencias. El responsable documenta después su resolución.
8. Los participantes autorizados consultan historial, registros, pagos y factura.

La factura se descarga como HTML. Para obtener un PDF, abre el archivo en el navegador y utiliza **Imprimir → Guardar como PDF**.

### Reglas del negocio

- Fechas en la zona del negocio: `America/Bogota` por defecto.
- Reservas de 1 a 20 unidades, máximo 90 días y sin inicio en el pasado.
- El día de devolución no se cobra: del 10 al 12 son dos días.
- El cliente paga y el proveedor responde dentro del mismo plazo: 24 horas, o hasta que termine el día de inicio si es antes. Cada solicitud muestra su hora límite. Pagar o aceptar después de ella se rechaza en el momento y la solicitud vence (con reembolso si se pagó). Para empezar el mismo día hay que solicitar antes de las 10:00 p. m.
- Consultar disponibilidad o enviar una solicitud no aparta unidades. **Pagar sí las aparta**, bajo bloqueo de la herramienta: si dos clientes pagan la última unidad a la vez, solo uno lo logra y al otro no se le cobra. Aceptar confirma las unidades apartadas.
- Si termina el periodo de una reserva aceptada sin que se haya podido entregar, se cancela y se reembolsa sola.
- El cliente paga al solicitar y el proveedor solo acepta reservas pagadas. Si la reserva se rechaza, vence, se cancela o el proveedor sale de la plataforma, el pago se reembolsa (simulado) y la factura queda anulada.
- No se permite entregar dos veces ni devolver antes de entregar. La devolución es completa.
- Una unidad dañada pasa a mantenimiento; una perdida pasa a retirada. Los daños o pérdidas abiertos impiden habilitarla manualmente.
- Una unidad entregada ocupa las fechas de su alquiler. Si pasa su fecha de devolución sin registrarla, bloquea además las fechas siguientes hasta que se devuelva. Una devolución anticipada libera las unidades aptas.
- El estado de reserva (`SOLICITADA`, `ACEPTADA`, `RECHAZADA`, `CANCELADA`, `VENCIDA`) se conserva separado del movimiento físico (`PENDIENTE_ENTREGA`, `EN_USO`, `DEVUELTA`).

### Estadísticas e informes

Administrador y proveedor pueden consultar reservas por estado, pagos aprobados de demostración, equipos en uso, devoluciones, incidencias abiertas y herramientas más solicitadas.

**El rango selecciona reservas por su fecha de solicitud y muestra su estado actual.** La suma de pagos corresponde a esas reservas; no representa ingresos agrupados por fecha de pago. El proveedor ve únicamente sus herramientas, incluidas las retiradas.

El administrador también descarga informes CSV para Excel de reservas e historial de eventos. Las descargas quedan registradas porque pueden contener datos personales.

## Tecnologías

| Herramienta | Uso |
| --- | --- |
| Java 21 | Lenguaje del backend y versión de compilación. |
| Maven / Maven Wrapper | Dependencias, compilación, pruebas y empaquetado. El wrapper fija Maven 3.9.12. |
| Spring Boot 4.1.1 / Spring MVC | Arranque y API REST con servidor embebido. |
| Spring Data JPA / Hibernate | Persistencia y acceso a datos. |
| MySQL | Base de datos normal; Compose utiliza MySQL 8.4. |
| Flyway | Migraciones versionadas para crear y actualizar el esquema. |
| Spring Security, JWT y BCrypt | Autenticación, permisos y hash de contraseñas. |
| Bean Validation | Validación de solicitudes. |
| HTML, CSS y JavaScript | Interfaz sin framework de frontend. |
| springdoc / Swagger UI | Documentación y pruebas interactivas de API. |
| JUnit, Spring Boot Test, H2 y ArchUnit | Pruebas de reglas, servicios, API y arquitectura. H2 solo se usa en pruebas. |
| Docker / Docker Compose | Ejecución de aplicación y MySQL con almacenamiento persistente. |
| Git / VS Code | Control de versiones y desarrollo. |

**No necesitas Node.js para ejecutar RentTool ni instalar un Tomcat externo.** Node.js es opcional para las comprobaciones de JavaScript.

## Estructura y arquitectura

La arquitectura hexagonal separa reglas de negocio y casos de uso de HTTP, base de datos y archivos.

```text
RentTool-/
├── pom.xml                 Padre Maven
├── domain/                 Modelos y reglas de negocio en Java puro
├── application/            Servicios y puertos de entrada/salida
├── infrastructure/         Spring Boot, HTTP, JPA, seguridad y archivos
│   └── src/main/resources/db/migration/
│                           Migraciones Flyway
├── frontend/               HTML, CSS, JavaScript e imágenes
├── docs/                   Documentación
├── scripts/                Comprobaciones JavaScript
├── compose.yaml            Aplicación y MySQL
├── compose.demo.yaml       Activación de datos demo
└── .env.example            Configuración de ejemplo para Compose
```

Dependencias: **infrastructure → application → domain**.

- `domain` contiene modelos y reglas sin depender de Spring ni JPA.
- `application` define casos de uso mediante `port/in`, acceso a servicios externos mediante `port/out` y los servicios que coordinan el negocio.
- `infrastructure` implementa adaptadores y configuración; genera el JAR ejecutable.
- Maven copia `frontend/` al JAR, por lo que interfaz y API comparten servidor.

Los contextos incluyen identidad, inventario, reservas, pagos y operaciones; también hay coordinación para disponibilidad, estadísticas, ajuste de unidades y baja de proveedores.

### VS Code y Java Projects

Abre **la raíz que contiene el pom.xml principal**, no solamente un módulo o `frontend/`. Con **Extension Pack for Java**, VS Code puede importar y mostrar los tres módulos Maven en Java Projects, junto con sus paquetes, pruebas y dependencias.

El arranque está en `infrastructure/src/main/java/com/renttool/infrastructure/RentToolApplication.java`. Los comandos siguientes no dependen de la configuración del botón Run.

## Sistemas compatibles

RentTool funciona en **Windows, macOS (Intel y Apple Silicon) y Linux**. Hay dos formas de ejecutarlo:

| Opción | Necesitas | Recomendada para |
| --- | --- | --- |
| [Docker](#ejecutar-con-docker) | Docker Desktop (Windows/macOS) o Docker Engine con el plugin Compose (Linux) | Probar o presentar el proyecto: incluye MySQL |
| [Java y MySQL locales](#ejecutar-con-java-y-mysql) | JDK 21 y MySQL 8 | Desarrollar sin contenedores |

Los comandos son iguales en los tres sistemas salvo en tres puntos: el wrapper de Maven (`.\mvnw.cmd` en Windows, `./mvnw` en macOS y Linux), la forma de copiar archivos y la de definir variables de entorno. Cada paso muestra la versión de **PowerShell** (Windows) y la de **bash/zsh** (macOS y Linux).

## Guía rápida en Linux

Sigue los pasos del caso que te corresponda. Todos usan Docker: **no necesitas instalar Java, Maven ni MySQL**. La primera ejecución necesita Internet.

| Caso | ¿Cuándo usarlo? |
| --- | --- |
| [Caso 1: sin sudo y sin datos de prueba](#caso-1-sin-sudo-y-sin-datos-de-prueba) | PCs sin permisos de administrador. Catálogo vacío. |
| [Caso 2: sin sudo y con datos de prueba](#caso-2-sin-sudo-y-con-datos-de-prueba) | PCs sin permisos de administrador. Carga Midnight y sus 18 herramientas. |
| [Caso 3: con sudo y sin datos de prueba](#caso-3-con-sudo-y-sin-datos-de-prueba) | Tienes permisos de administrador. Catálogo vacío. |
| [Caso 4: con sudo y con datos de prueba](#caso-4-con-sudo-y-con-datos-de-prueba) | Tienes permisos de administrador. Carga Midnight y sus 18 herramientas. |

### Valores del archivo .env

En todos los casos se crea un archivo `.env` (solo la primera vez). Al abrirlo con `nano`, reemplaza los valores de ejemplo por los tuyos:

```dotenv
APP_PORT=8088
DB_PASSWORD=tu_clave_mysql_local
MYSQL_ROOT_PASSWORD=tu_clave_root_mysql
JWT_SECRET=tu_secreto_aleatorio_de_al_menos_32_caracteres
RENTTOOL_ADMIN_EMAIL=admin@example.com
RENTTOOL_ADMIN_PASSWORD=tu_clave_admin_de_12_a_64_caracteres
DEMO_PASSWORD=tu_clave_demo_de_al_menos_10_caracteres
```

- `JWT_SECRET`: al menos 32 caracteres. Puedes generarlo con `openssl rand -hex 32`.
- `RENTTOOL_ADMIN_EMAIL` y `RENTTOOL_ADMIN_PASSWORD`: serán el usuario y la contraseña del **administrador** (contraseña de 12 a 64 caracteres).
- `DEMO_PASSWORD`: será la contraseña de **Midnight** (al menos 10 caracteres). Solo se usa en los casos con datos de prueba.

En nano, guarda con **Ctrl+O**, pulsa **Enter** y sal con **Ctrl+X**. **Anota las contraseñas:** cambiarlas después en `.env` no cambia las de cuentas ya creadas.

### Caso 1: sin sudo y sin datos de prueba

**Primero:** abre una terminal en la carpeta del proyecto (donde está `compose.yaml`).

```bash
cd ~/Escritorio/RentTool-
```

**Segundo:** comprueba que Docker responde sin sudo.

```bash
docker info
```

Si muestra un error de permisos o de conexión, selecciona Docker rootless y vuelve a comprobar:

```bash
docker context use rootless
docker info
```

Si aún falla, inicia el motor de tu usuario con `systemctl --user start docker` y repite `docker info`.

**Tercero:** crea el archivo `.env` (solo la primera vez) y rellénalo con [estos valores](#valores-del-archivo-env).

```bash
test -f .env || cp .env.example .env
nano .env
```

**Cuarto:** construye y arranca RentTool.

```bash
docker compose up --build -d
```

**Quinto:** espera a que termine de arrancar.

```bash
docker compose logs -f app
```

Cuando aparezca `Started RentToolApplication`, pulsa **Ctrl+C** (la aplicación sigue funcionando).

**Sexto:** abre **http://localhost:8088** e inicia sesión como administrador con `RENTTOOL_ADMIN_EMAIL` y `RENTTOOL_ADMIN_PASSWORD`.

**Para detener:**

```bash
docker compose down
```

**Para volver a iniciar otro día:** repite los pasos primero, segundo, cuarto y quinto.

### Caso 2: sin sudo y con datos de prueba

**Primero:** abre una terminal en la carpeta del proyecto (donde está `compose.yaml`).

```bash
cd ~/Escritorio/RentTool-
```

**Segundo:** comprueba que Docker responde sin sudo.

```bash
docker info
```

Si muestra un error de permisos o de conexión, selecciona Docker rootless y vuelve a comprobar:

```bash
docker context use rootless
docker info
```

Si aún falla, inicia el motor de tu usuario con `systemctl --user start docker` y repite `docker info`.

**Tercero:** crea el archivo `.env` (solo la primera vez) y rellénalo con [estos valores](#valores-del-archivo-env). **No olvides `DEMO_PASSWORD`.**

```bash
test -f .env || cp .env.example .env
nano .env
```

**Cuarto:** construye y arranca RentTool con los datos de prueba (incluye **los dos** archivos `-f`).

```bash
docker compose -f compose.yaml -f compose.demo.yaml up --build -d
```

**Quinto:** espera a que termine de arrancar.

```bash
docker compose -f compose.yaml -f compose.demo.yaml logs -f app
```

Cuando aparezcan `Started RentToolApplication` y `Midnight publicó 18 herramientas de demostración`, pulsa **Ctrl+C** (la aplicación sigue funcionando). En arranques siguientes el segundo mensaje no aparece porque las herramientas ya existen.

**Sexto:** abre **http://localhost:8088**. Cuentas disponibles:

| Cuenta | Correo | Contraseña |
| --- | --- | --- |
| Administrador | El de `RENTTOOL_ADMIN_EMAIL` | La de `RENTTOOL_ADMIN_PASSWORD` |
| Proveedor Midnight | `midnight@renttool.demo` | La de `DEMO_PASSWORD` |

**Para detener:**

```bash
docker compose -f compose.yaml -f compose.demo.yaml down
```

**Para volver a iniciar otro día:** repite los pasos primero, segundo, cuarto y quinto.

### Caso 3: con sudo y sin datos de prueba

**Primero:** abre una terminal en la carpeta del proyecto (donde está `compose.yaml`).

```bash
cd ~/Escritorio/RentTool-
```

**Segundo:** comprueba que Docker responde.

```bash
sudo docker info
```

Si Docker no está instalado (Ubuntu/Debian): `sudo apt install docker.io docker-compose-v2`.

**Tercero:** crea el archivo `.env` (solo la primera vez) y rellénalo con [estos valores](#valores-del-archivo-env).

```bash
test -f .env || cp .env.example .env
nano .env
```

**Cuarto:** construye y arranca RentTool.

```bash
sudo docker compose up --build -d
```

**Quinto:** espera a que termine de arrancar.

```bash
sudo docker compose logs -f app
```

Cuando aparezca `Started RentToolApplication`, pulsa **Ctrl+C** (la aplicación sigue funcionando).

**Sexto:** abre **http://localhost:8088** e inicia sesión como administrador con `RENTTOOL_ADMIN_EMAIL` y `RENTTOOL_ADMIN_PASSWORD`.

**Para detener:**

```bash
sudo docker compose down
```

**Para volver a iniciar otro día:** repite los pasos primero, cuarto y quinto.

### Caso 4: con sudo y con datos de prueba

**Primero:** abre una terminal en la carpeta del proyecto (donde está `compose.yaml`).

```bash
cd ~/Escritorio/RentTool-
```

**Segundo:** comprueba que Docker responde.

```bash
sudo docker info
```

Si Docker no está instalado (Ubuntu/Debian): `sudo apt install docker.io docker-compose-v2`.

**Tercero:** crea el archivo `.env` (solo la primera vez) y rellénalo con [estos valores](#valores-del-archivo-env). **No olvides `DEMO_PASSWORD`.**

```bash
test -f .env || cp .env.example .env
nano .env
```

**Cuarto:** construye y arranca RentTool con los datos de prueba (incluye **los dos** archivos `-f`).

```bash
sudo docker compose -f compose.yaml -f compose.demo.yaml up --build -d
```

**Quinto:** espera a que termine de arrancar.

```bash
sudo docker compose -f compose.yaml -f compose.demo.yaml logs -f app
```

Cuando aparezcan `Started RentToolApplication` y `Midnight publicó 18 herramientas de demostración`, pulsa **Ctrl+C** (la aplicación sigue funcionando). En arranques siguientes el segundo mensaje no aparece porque las herramientas ya existen.

**Sexto:** abre **http://localhost:8088**. Cuentas disponibles:

| Cuenta | Correo | Contraseña |
| --- | --- | --- |
| Administrador | El de `RENTTOOL_ADMIN_EMAIL` | La de `RENTTOOL_ADMIN_PASSWORD` |
| Proveedor Midnight | `midnight@renttool.demo` | La de `DEMO_PASSWORD` |

**Para detener:**

```bash
sudo docker compose -f compose.yaml -f compose.demo.yaml down
```

**Para volver a iniciar otro día:** repite los pasos primero, cuarto y quinto.

### Importante en todos los casos

- **Los datos se conservan** entre arranques (volúmenes `mysql_data` y `uploads`). No añadas `-v` a `down` si quieres conservarlos.
- **Para empezar de cero** (borra usuarios, reservas, herramientas e imágenes): `docker compose -f compose.yaml -f compose.demo.yaml down -v` (antepón `sudo` en los casos 3 y 4).
- **Una vez cargados, los datos de prueba no desaparecen** al arrancar sin `compose.demo.yaml`. Para tener un catálogo vacío después de usar los casos 2 o 4, empieza de cero.
- **Con sudo y sin sudo son instalaciones separadas:** cada una tiene sus propios contenedores y datos. Usa siempre la misma forma en un mismo PC.
- **Ver errores:** `docker compose logs --tail=100 app mysql` (antepón `sudo` en los casos 3 y 4).
- Si aparece un error que menciona `~/.docker/desktop/docker.sock`, Docker intenta conectar con Docker Desktop: ejecuta `docker context use rootless`.
- Si el puerto 8088 está ocupado, cambia `APP_PORT` en `.env` y abre `http://localhost:<nuevo puerto>`.

## Ejecutar con Docker

Esta opción incluye MySQL y la aplicación en contenedores. No requiere instalar Java, Maven ni MySQL. Las imágenes (`maven`, `eclipse-temurin` y `mysql:8.4`) existen para x86-64 y ARM, así que también funcionan en Mac con chip M.

### 1. Preparar Docker

- **Windows y macOS:** instala y abre Docker Desktop. Espera a que el motor esté listo.
- **Linux:** si no tienes sudo o quieres pasos detallados, usa la [Guía rápida en Linux](#guía-rápida-en-linux). Si no, instala Docker Engine y el plugin Compose (en Ubuntu/Debian: `sudo apt install docker.io docker-compose-v2`, o sigue la guía oficial de Docker). Para usar `docker` sin `sudo`: `sudo usermod -aG docker $USER` y vuelve a iniciar sesión.

La primera construcción necesita Internet. Comprueba:

```bash
docker --version
docker compose version
```

### 2. Abrir el repositorio

Desde la terminal, en la carpeta que contiene `pom.xml` y `compose.yaml`:

```powershell
# Windows (PowerShell)
cd "C:\ruta\RentTool-"
```

```bash
# macOS y Linux
cd ~/ruta/RentTool-
```

### 3. Configurar el archivo .env

Solo si todavía no existe:

```powershell
# Windows (PowerShell)
Copy-Item .env.example .env
notepad .env
```

```bash
# macOS y Linux
cp .env.example .env
nano .env        # o: open -e .env (macOS)
```

Reemplaza los ejemplos por tus valores:

```dotenv
APP_PORT=8088
DB_PASSWORD=tu_clave_mysql_local
MYSQL_ROOT_PASSWORD=tu_clave_root_mysql
JWT_SECRET=tu_secreto_aleatorio_de_al_menos_32_caracteres
RENTTOOL_ADMIN_EMAIL=admin@example.com
RENTTOOL_ADMIN_PASSWORD=tu_clave_admin_de_12_a_64_caracteres
DEMO_PASSWORD=tu_clave_demo_de_al_menos_10_caracteres
```

`DEMO_PASSWORD` solo se utiliza con el perfil demo. `.env` está excluido de Git. No sobrescribas un archivo ya configurado si deseas conservar sus valores.

### 4. Construir y arrancar

Para iniciar sin cargar catálogo demo (igual en los tres sistemas):

```bash
docker compose up --build
```

Si quieres cargar herramientas de ejemplo, usa en su lugar el comando de [Catálogo de demostración](#catálogo-de-demostración).

Deja abierta la terminal. Espera a que MySQL esté saludable y aparezca **Started RentToolApplication**.

Desde otra terminal puedes revisar:

```bash
docker compose ps
docker compose logs --tail=100 app
```

### 5. Abrir la aplicación

| Página | Dirección predeterminada |
| --- | --- |
| Sitio público e inicio de sesión | http://localhost:8088 |
| Proveedor | http://localhost:8088/proveedor |
| Administrador | http://localhost:8088/admin |
| Swagger UI | http://localhost:8088/swagger-ui.html |
| Especificación OpenAPI | http://localhost:8088/v3/api-docs |

Inicia sesión como administrador con las credenciales configuradas en `.env`. No existe una contraseña universal. Cada panel requiere el rol correspondiente.

En Swagger, obtén un token con `POST /api/v1/auth/login` y utiliza **Authorize** para probar rutas protegidas.

### 6. Detener y reiniciar

```bash
docker compose down
```

Para volver a iniciar en segundo plano:

```bash
docker compose up -d
```

Los volúmenes `mysql_data` y `uploads` conservan datos e imágenes. **No añadas -v al apagar si deseas conservarlos.** MySQL solo se publica dentro de la red de Compose; RentTool se expone en el puerto local de `APP_PORT`.

## Ejecutar con Java y MySQL

Esta alternativa usa Java y MySQL instalados en tu equipo, sin necesidad de Docker.

### 1. Instalar y comprobar los requisitos

Instala **JDK 21** y **MySQL 8** e inicia el servicio MySQL. MySQL Workbench es opcional.

| Sistema | Instalación sugerida |
| --- | --- |
| Windows | Instaladores de [Eclipse Temurin 21](https://adoptium.net) y [MySQL Community](https://dev.mysql.com/downloads/installer/) |
| macOS | `brew install openjdk@21 mysql` y `brew services start mysql` |
| Ubuntu/Debian | `sudo apt install openjdk-21-jdk mysql-server` (el servicio arranca solo) |

Desde la raíz del repositorio:

```powershell
# Windows (PowerShell)
java -version
.\mvnw.cmd -version
```

```bash
# macOS y Linux
java -version
./mvnw -version
```

Si en macOS o Linux aparece `permission denied`, da permiso de ejecución una vez: `chmod +x mvnw`.

Verifica que el wrapper use el JDK correcto. No necesitas instalar Maven por separado; la primera descarga requiere Internet.

### 2. Crear base de datos y usuario

Desde MySQL Workbench o una consola MySQL con una cuenta administradora (en Linux suele ser `sudo mysql`; en macOS con Homebrew, `mysql -u root`):

```sql
CREATE DATABASE IF NOT EXISTS renttool CHARACTER SET utf8mb4;
CREATE USER IF NOT EXISTS 'renttool'@'localhost'
  IDENTIFIED BY 'tu_clave_mysql_local';
GRANT ALL PRIVILEGES ON renttool.* TO 'renttool'@'localhost';
```

Usa la misma contraseña en `DB_PASSWORD`. Si el usuario ya existe, este comando no cambia su clave: utiliza la existente o actualízala en MySQL.

No crees tablas manualmente. Flyway aplica las migraciones y Hibernate valida el esquema al arrancar.

### 3. Definir variables de entorno

En la misma terminal donde ejecutarás Java.

**Windows (PowerShell):**

```powershell
$env:DB_URL = "jdbc:mysql://localhost:3306/renttool"
$env:DB_USER = "renttool"
$env:DB_PASSWORD = "tu_clave_mysql_local"
$env:JWT_SECRET = "tu_secreto_aleatorio_de_al_menos_32_caracteres"
$env:RENTTOOL_ADMIN_EMAIL = "admin@example.com"
$env:RENTTOOL_ADMIN_PASSWORD = "tu_clave_admin_de_12_a_64_caracteres"
$env:SERVER_PORT = "8088"
```

**macOS y Linux (bash/zsh):**

```bash
export DB_URL="jdbc:mysql://localhost:3306/renttool"
export DB_USER="renttool"
export DB_PASSWORD="tu_clave_mysql_local"
export JWT_SECRET="tu_secreto_aleatorio_de_al_menos_32_caracteres"
export RENTTOOL_ADMIN_EMAIL="admin@example.com"
export RENTTOOL_ADMIN_PASSWORD="tu_clave_admin_de_12_a_64_caracteres"
export SERVER_PORT="8088"
```

Sustituye las credenciales. Las variables duran lo que dure esa terminal: si la cierras, debes volver a configurarlas.

**El JAR no carga automáticamente .env.** Ese archivo se utiliza con Compose. En ejecución local define las variables o configura expresamente el entorno de VS Code.

El administrador se crea al arrancar si no existe y configuraste sus datos. Cambiar las variables posteriormente no restablece la contraseña de una cuenta existente ni convierte a un cliente en administrador.

### 4. Compilar y probar

```powershell
# Windows (PowerShell)
.\mvnw.cmd clean verify
```

```bash
# macOS y Linux
./mvnw clean verify
```

Espera a **BUILD SUCCESS**. Las pruebas usan H2 y no necesitan MySQL local. Si ya tienes Maven instalado, puedes usar `mvn clean verify`.

### 5. Iniciar el JAR

Con MySQL activo y en la misma terminal de las variables:

```powershell
# Windows (PowerShell)
java -jar .\infrastructure\target\renttool-infrastructure-0.1.0-SNAPSHOT.jar
```

```bash
# macOS y Linux
java -jar infrastructure/target/renttool-infrastructure-0.1.0-SNAPSHOT.jar
```

Espera a **Started RentToolApplication** y abre http://localhost:8088. Las demás páginas utilizan las rutas de la tabla anterior.

Sin `SERVER_PORT`, Java local usa **8080**. `APP_PORT` solo controla el puerto publicado por Docker Compose.

Para detener Java pulsa **Ctrl+C**. Para reiniciar, ejecuta nuevamente el JAR con las variables configuradas.

## Catálogo de demostración

El perfil `demo` crea el proveedor **Midnight**, cuenta `midnight@renttool.demo`, y 18 herramientas con imágenes y unidades en Eléctricas, Iluminación y Manuales.

### Con Docker

Configura `DEMO_PASSWORD` en `.env` y arranca con (igual en los tres sistemas):

```bash
docker compose -f compose.yaml -f compose.demo.yaml up --build
```

Para detener:

```bash
docker compose -f compose.yaml -f compose.demo.yaml down
```

### Con Java local

Después de configurar las variables de la ejecución local:

```powershell
# Windows (PowerShell)
$env:SPRING_PROFILES_ACTIVE = "demo"
$env:DEMO_PASSWORD = "tu_clave_demo_de_al_menos_10_caracteres"
java -jar .\infrastructure\target\renttool-infrastructure-0.1.0-SNAPSHOT.jar
```

```bash
# macOS y Linux
export SPRING_PROFILES_ACTIVE="demo"
export DEMO_PASSWORD="tu_clave_demo_de_al_menos_10_caracteres"
java -jar infrastructure/target/renttool-infrastructure-0.1.0-SNAPSHOT.jar
```

Para volver a iniciar sin el perfil, elimina las variables antes del siguiente arranque:

```powershell
# Windows (PowerShell)
Remove-Item Env:SPRING_PROFILES_ACTIVE -ErrorAction SilentlyContinue
Remove-Item Env:DEMO_PASSWORD -ErrorAction SilentlyContinue
```

```bash
# macOS y Linux
unset SPRING_PROFILES_ACTIVE DEMO_PASSWORD
```

La contraseña de Midnight es la definida al crear esa cuenta. El inicializador evita duplicar sus publicaciones y no restablece contraseñas existentes. Si detecta la cuenta demo anterior `proveedor@renttool.demo`, la da de baja y retira sus herramientas conservando el historial. Usa el perfil en una base destinada a demostración.

## Probar un alquiler completo

1. Carga el catálogo demo o crea un proveedor desde el administrador y publica una herramienta con unidades operativas.
2. Registra un cliente desde el sitio público y completa su teléfono.
3. Solicita una reserva que **comience hoy** y termine después, y págala con la tarjeta de prueba `4242 4242 4242 4242` (cualquier fecha futura y CVV `123`). Descarga la factura.
4. Como proveedor, acepta la solicitud.
5. La entrega queda registrada sola al aceptar (empieza hoy). Como proveedor, registra después la devolución de todas las unidades.
6. Para probar incidencias, devuelve una unidad como dañada, escribe el motivo y registra luego su resolución.
7. Consulta estadísticas con un rango que incluya el día de solicitud de la reserva.

Puedes usar otra ventana privada u otro navegador para mantener sesiones de distintos roles.

## Configuración

### Archivos properties y perfiles

La configuracion de Spring vive en `infrastructure/src/main/resources`, el modulo que arranca la aplicacion. Se adapto la separacion de perfiles del proyecto de referencia JWT a los adaptadores y variables de RentTool:

| Archivo | Responsabilidad |
| --- | --- |
| `application.properties` | Conexion comun, Flyway, JWT, archivos, CORS, zona horaria y rutas de Swagger. |
| `application-dev.properties` | Desarrollo predeterminado, pool local, logs y Swagger habilitado. |
| `application-prod.properties` | Conexion obligatoria desde el entorno, logs reducidos, compresion y Swagger deshabilitado. |
| `application-*.properties.example` | Referencias sin secretos; Spring no las carga. |

Sin perfil explicito se utiliza `dev`. Para produccion define `SPRING_PROFILES_ACTIVE=prod` junto con `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `RENTTOOL_ADMIN_EMAIL` y `RENTTOOL_ADMIN_PASSWORD`. Configura TLS en `DB_URL` segun tu servidor MySQL. En Docker Compose, agrega `SPRING_PROFILES_ACTIVE: prod` al bloque `environment` del servicio `app`.

El perfil existente `demo` sigue funcionando. Para combinarlo con ajustes de desarrollo usa `SPRING_PROFILES_ACTIVE=dev,demo`; requiere tambien `DEMO_PASSWORD`. Las variables actuales de Compose y los comandos existentes conservan su funcionamiento. El JAR no carga `.env` automaticamente.

Flyway sigue siendo responsable del esquema y `ddl-auto=validate` se conserva en ambos entornos. Los secretos no tienen valores predeterminados. CORS utiliza `renttool.cors-origins`, consumido por `WebConfig`; no se copian propiedades de CORS o Actuator sin soporte en el proyecto. Para ver SQL en desarrollo define `SHOW_SQL=true`.


| Variable | Función |
| --- | --- |
| `DB_URL` | JDBC de MySQL. Local: localhost:3306; Compose configura el host interno mysql. |
| `DB_USER` / `DB_PASSWORD` | Usuario y contraseña de la aplicación. Usuario local predeterminado: renttool. |
| `MYSQL_ROOT_PASSWORD` | Clave inicial de root para MySQL en Compose. |
| `JWT_SECRET` | Secreto para firmar tokens; mínimo 32 caracteres. |
| `RENTTOOL_ADMIN_EMAIL` / `RENTTOOL_ADMIN_PASSWORD` | Alta inicial del administrador; contraseña de 12 a 64 caracteres. Compose exige ambas. |
| `APP_PORT` | Puerto local publicado por Docker; predeterminado 8088. |
| `SERVER_PORT` | Puerto del proceso Java; predeterminado 8080. |
| `RENTTOOL_UPLOAD_DIR` | Carpeta de imágenes; predeterminada uploads, relativa al directorio de ejecución. |
| `RENTTOOL_CORS_ORIGINS` | Orígenes permitidos por CORS cuando se separa interfaz y API. |
| `SPRING_PROFILES_ACTIVE` / `DEMO_PASSWORD` | Activación y contraseña de la demostración. |
| `RENTTOOL_TIME_ZONE` | Zona del negocio; predeterminada America/Bogota. |

Las contraseñas se almacenan con BCrypt. El JWT se conserva en `sessionStorage`; el servidor verifica que la cuenta siga activa en cada petición autenticada. La sesión administrativa vence a los 10 minutos, con renovación mientras hay actividad y límite de 8 horas desde el inicio de sesión.

## Pruebas y actualización

### Backend

```powershell
.\mvnw.cmd verify    # Windows
```

```bash
./mvnw verify        # macOS y Linux
```

Ejecuta pruebas de dominio, servicios, API y arquitectura. H2 en modo MySQL facilita la automatización, pero no sustituye una validación contra MySQL.

### JavaScript: comprobaciones opcionales con Node.js

```bash
node --check frontend/app.js
node --check frontend/statistics.js
node scripts/check-errors.mjs
node scripts/check-confirmations.mjs
node scripts/check-availability.mjs
```

### Aplicar cambios del código

El frontend se copia al JAR al compilar. Después de modificar Java, HTML, CSS o JavaScript:

- **Docker:** ejecuta otra vez `docker compose up --build`; incluye ambos archivos con `-f` si utilizas demo.
- **Java local:** detén el JAR, ejecuta `.\mvnw.cmd clean verify` (Windows) o `./mvnw clean verify` (macOS y Linux) y vuelve a arrancarlo con sus variables.

Recarga el navegador con **Ctrl+F5** (Windows y Linux) o **Cmd+Shift+R** (macOS) si sigue mostrando archivos anteriores.

## Solución de problemas

| Problema | Revisión |
| --- | --- |
| Failed to load page o localhost no abre | Comprueba que el proceso siga activo y haya mostrado Started RentToolApplication. Revisa el puerto: 8088 con estas instrucciones o 8080 si no configuraste SERVER_PORT en Java local. |
| Página abre, pero catálogo falla | Usa el servidor de Java; no abras index.html con file:// ni con Live Server. Revisa los registros. |
| Docker no conecta con el motor | Abre Docker Desktop y espera a que esté listo. Ejecuta docker compose ps desde la raíz. |
| No conecta con MySQL | Revisa servicio, puerto, base y credenciales. Java local utiliza localhost; el contenedor utiliza mysql. |
| Edité .env y MySQL rechaza la clave | Un volumen existente conserva las claves originales. Cambiar .env no modifica usuarios ya creados: usa la clave original o actualízala en MySQL. |
| Falta DB_PASSWORD o JWT_SECRET | Define las variables en la misma terminal de Java. El JAR no lee .env automáticamente. |
| Java o wrapper no funcionan | Revisa JDK 21, JAVA_HOME, PATH y la versión mostrada por el wrapper; la primera descarga necesita Internet. |
| `./mvnw: permission denied` (macOS/Linux) | Ejecuta `chmod +x mvnw` una vez. |
| `/bin/sh^M: bad interpreter` (macOS/Linux) | El archivo `mvnw` llegó con finales de línea de Windows. Vuelve a descargarlo con `git checkout -- mvnw`; `.gitattributes` evita que se repita. |
| Docker pide `sudo` en Linux | Sin permisos de administrador, usa Docker rootless: `docker context use rootless` (ver [Guía rápida en Linux](#guía-rápida-en-linux)). Con permisos, agrega tu usuario al grupo docker (`sudo usermod -aG docker $USER`) y vuelve a iniciar sesión. |
| Puerto ocupado | Cambia APP_PORT en Docker o SERVER_PORT en Java y abre la nueva dirección. |
| Administrador no inicia sesión | Usa sus credenciales originales. Las variables de provisionamiento no cambian su contraseña después de crearlo. |
| Proveedor no puede operar | Revisa rol, cuenta activa y cambio obligatorio de contraseña temporal. |
| No permite entregar | La reserva debe estar aceptada, pagada, dentro del período y con unidades aptas. |
| Estadísticas vacías | Elige fechas que incluyan la solicitud, no solo la entrega o el pago; el proveedor solo ve sus herramientas. |
| Error de Flyway | Revisa el mensaje, migración y base conectada. No modifiques migraciones aplicadas ni borres el historial como solución automática. |

Registros con Docker:

```bash
docker compose logs --tail=100 app mysql
```

En Java local, revisa la terminal del JAR: el error final suele identificar la causa del fallo de arranque.

## Limitaciones y documentación

- Sin pasarela real, reembolsos ni facturación fiscal.
- Sin devoluciones parciales, transporte integrado, multas o cobros automáticos por daños.
- Algunas listas de gestión cargan hasta 100 herramientas o 50 reservas; el catálogo y determinadas consultas sí ofrecen paginación.
- Las consultas de estadísticas y disponibilidad están orientadas al tamaño de la demostración; para grandes volúmenes habría que optimizar consultas y agregaciones.
- Quitar una imagen de una publicación no borra automáticamente su archivo físico.
- La gestión global de usuarios y otros requisitos de la planeación quedan para siguientes incrementos.

| Documento | Contenido |
| --- | --- |
| [Arquitectura](docs/ARQUITECTURA.md) | Capas, contextos, puertos y adaptadores. |
| [Modelo de datos](docs/MODELO_DATOS.md) | Tablas y relaciones. |
| [API](docs/API_MVP1.md) | Rutas y contratos. |
| [Pagos demo](docs/PAGOS_DEMO.md) | Pasarela simulada, datos de prueba, reembolsos y factura. |
| [Entregas y devoluciones](docs/ENTREGAS_DEVOLUCIONES.md) | Operación física, daños y pérdidas. |
| [Estadísticas y disponibilidad](docs/ESTADISTICAS_DISPONIBILIDAD.md) | Filtros, permisos e interpretación. |
| [Planeación](docs/PLANEACION_PROYECTO.md) | Incrementos previstos. |
| [Especificación](docs/DESGLOSE_PROYECTO_IA.md) | Planteamiento y requerimientos. |
| [Contexto conservado](docs/MEMORIA_PROYECTO.md) | Antecedentes del proyecto. |

Para ejecutar la versión actual, utiliza los pasos de este README. La planeación incluye también funciones todavía no implementadas.
