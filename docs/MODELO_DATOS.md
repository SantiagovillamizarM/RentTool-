# Modelo implementado — identidad, inventario y reservas

Migraciones: `V1__identity_inventory.sql` y `V2__reservations.sql` en `infrastructure/src/main/resources/db/migration/`.

```mermaid
erDiagram
 USERS ||--o{ TOOLS : publica
 CATEGORIES ||--o{ TOOLS : clasifica
 TOOLS ||--o{ UNITS : contiene
 TOOLS ||--o{ TOOL_IMAGES : muestra
 USERS ||--o{ RESERVATIONS : solicita
 TOOLS ||--o{ RESERVATIONS : reserva
 RESERVATIONS ||--o{ RESERVATION_UNITS : asigna
 UNITS ||--o{ RESERVATION_UNITS : ocupa
 USERS { bigint id PK
 varchar email UK
 varchar name
 varchar password_hash
 varchar role
 boolean active }
 CATEGORIES { bigint id PK
 varchar name UK
 varchar icon }
 TOOLS { bigint id PK
 bigint provider_id FK
 bigint category_id FK
 varchar name
 varchar description
 decimal daily_rate
 boolean active }
 UNITS { bigint id PK
 bigint tool_id FK
 varchar code
 varchar status }
 TOOL_IMAGES { bigint id PK
 bigint tool_id FK
 varchar location }
 RESERVATIONS { bigint id PK
 bigint client_id FK
 bigint tool_id FK
 date start_date
 date end_date
 int quantity
 decimal daily_rate
 decimal total
 varchar status
 datetime requested_at
 datetime respond_by
 varchar note }
 RESERVATION_UNITS { bigint reservation_id PK,FK
 bigint unit_id PK,FK }
```

Claves candidatas: users.id y users.email; categories.id y categories.name; tools.id; units.id y (tool_id, code); tool_images.id. Cada clave determina todos los demás atributos de su relación. Las colecciones independientes imágenes y unidades se descomponen: no existe una relación que multiplique cada imagen por cada unidad. En estas relaciones no se identifica una dependencia multivaluada no trivial con determinante que no sea superclave; así se evita la violación de 4FN derivada de dichas colecciones. Cada publicación pertenece a un proveedor y una categoría.

Tarifa DECIMAL(14,2) positiva; estado de unidades restringido por CHECK; FKs preservan referencias y unicidad evita repetir códigos por publicación. Índices cubren herramientas por proveedor/activo y categoría/activo. Roles se validan en aplicación; el dominio evita tarifas no positivas.

Reservas: cada fila depende solo de su id. `daily_rate` y `total` se guardan a propósito como valores históricos pactados al solicitar, no como copia derivable de la tarifa actual. El proveedor no se duplica en la reserva: se obtiene a través de la herramienta. Las unidades asignadas son un hecho multivaluado independiente y viven en `reservation_units` con clave compuesta (reserva, unidad), en lugar de una lista dentro de una columna, para cumplir 4FN. CHECK restringen estados, cantidad (1–20), importes positivos y `end_date > start_date`. Índices cubren la búsqueda de solapamientos (herramienta, estado, fechas), las reservas por cliente, el vencimiento (estado, plazo) y las asignaciones por unidad.

El modelo completo propuesto en el desglose incluye además perfiles, pagos, facturas, entregas, devoluciones e incidencias. Esas tablas aún no se crean. El diagrama como imagen y los 20 registros por entidad para entrega académica se completarán con los siguientes MVP; seis publicaciones demo no satisfacen ese entregable final.
