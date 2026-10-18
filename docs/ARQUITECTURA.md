# Arquitectura hexagonal

El proyecto es un monolito modular organizado por capas. Cada capa es un módulo Maven con su propio `pom.xml`, y las dependencias apuntan siempre hacia el dominio.

```text
infrastructure  ──►  application  ──►  domain
 (web, JPA,          (casos de uso,     (agregados, valores,
  JWT, archivos)      puertos)           reglas de negocio)
```

| Módulo | Artefacto | Depende de | Contenido |
|---|---|---|---|
| `domain/` | `renttool-domain` | nada | Agregados, valores, reglas y excepciones de dominio. Java puro. |
| `application/` | `renttool-application` | `domain` | Casos de uso, puertos de entrada y salida, paginación. Sin Spring. |
| `infrastructure/` | `renttool-infrastructure` | `application` + Spring Boot | Adaptadores, configuración, migraciones, arranque y JAR ejecutable. |

Como `domain` y `application` no tienen Spring ni JPA en su classpath, el compilador rechaza cualquier import de framework en esas capas.

## Paquetes

```text
domain/          com.renttool.domain
                   identity/         Account, Role
                   inventory/        Tool, Unit, UnitStatus, ToolImage, Category, Actor
                   shared/exception/ InvalidInput, NotFound, ForbiddenOperation, Conflict, AuthenticationFailed

application/     com.renttool.application
                   <contexto>/port/in/   casos de uso que ofrece el contexto (interfaces + comandos)
                   <contexto>/port/out/  lo que necesita del exterior (repositorios, almacenamiento, tokens)
                   <contexto>/service/   implementación de los casos de uso
                   shared/               PageQuery, PageResult

infrastructure/  com.renttool.infrastructure
                   RentToolApplication   arranque de Spring Boot
                   <contexto>/web/           adaptador de entrada: controllers y DTOs
                   <contexto>/persistence/   adaptador de salida: entidades JPA, Spring Data y mapeadores
                   <contexto>/security/ | external/   JWT, BCrypt, disco local
                   <contexto>/config/        ensambla servicios con adaptadores (@Bean)
                   config/                   seguridad HTTP, CORS, inicializadores (admin, demo) y GlobalExceptionHandler
```

Las excepciones de dominio no conocen HTTP: `GlobalExceptionHandler` las traduce a 400/401/403/404/409.

## Puertos actuales

| Contexto | Entrada (`port/in`) | Salida (`port/out`) | Adaptadores de salida |
|---|---|---|---|
| Identidad | `RegisterAccountUseCase`, `LoginUseCase`, `GetProfileUseCase`, `UpdateProfileUseCase`, `ProvisionAccountUseCase` | `AccountRepository`, `PasswordHasher`, `TokenIssuer` | `AccountPersistenceAdapter`, `BCryptPasswordHasher`, `JwtTokenIssuer` |
| Inventario | `BrowseCatalogUseCase`, `ManageToolsUseCase`, `ManageUnitsUseCase`, `ManageImagesUseCase` | `ToolRepository`, `UnitRepository`, `ToolImageRepository`, `CategoryRepository`, `ImageStorage` | `*PersistenceAdapter`, `LocalImageStorage` |

Inventario no depende de identidad: el adaptador web traduce el JWT a un `Actor` (id y rol) del dominio de inventario, y la regla de propiedad (`Tool.ensureManageableBy`) vive en el agregado.

## Reglas verificadas

- **Maven**: `domain` no ve `application`, `infrastructure` ni frameworks; `application` no ve `infrastructure` ni frameworks.
- **`HexagonalArchitectureTest`** (ArchUnit, en `infrastructure`):
  - los controllers solo usan puertos de entrada, no servicios, puertos de salida ni persistencia;
  - las entidades JPA solo se usan dentro de su adaptador de persistencia;
  - identidad e inventario no dependen uno del otro.

Pruebas por capa: `domain` prueba reglas del dominio y `application` prueba casos de uso con puertos en memoria, ambos sin Spring. `infrastructure` prueba la API completa y la arquitectura.

## Añadir un contexto (p. ej. `rental`)

1. Modelar agregados y reglas en `domain/.../domain/rental`.
2. Declarar casos de uso en `application/.../rental/port/in` y necesidades externas en `rental/port/out`.
3. Implementar los casos de uso en `application/.../rental/service` usando solo puertos.
4. Crear adaptadores en `infrastructure/.../rental/` y ensamblarlos en `rental/config`.
5. Si necesita datos de otro contexto, consumir sus puertos de entrada, nunca sus repositorios.

## Contexto billing

`domain/billing` contiene Payment e Invoice. `application/billing` contiene los casos de uso, PaymentService y los puertos para consultar reservas y guardar pagos. `infrastructure/billing` implementa repositorios JPA, controladores y configuración. Pago aprobado y factura se guardan en una transacción. Se conservan los tres módulos Maven y la separación de capas. No hay pasarela externa, claves ni webhooks.
