# API implementada — MVP 01

Base `/api/v1`, JSON excepto subida de archivos. Autenticación: `Authorization: Bearer <token>` obtenido por registro/login. El registro público solo crea `CLIENTE`; los proveedores los da de alta el administrador.

| Método | Ruta | Operación |
|---|---|---|
| POST | /auth/register | name, email, password (10–64 caracteres); role opcional y solo `CLIENTE` (otro valor: 403) |
| POST | /auth/login | email, password; devuelve token, expiresAt y user |
| POST | /auth/refresh | Con token vigente: emite uno nuevo para la misma sesión (máximo 8 horas desde el login) |
| GET/PUT | /me | Perfil propio (incluye `mustChangePassword` y, para proveedores, `logoUrl`); PUT modifica name, salvo el del administrador, que es fijo (403) |
| PUT | /me/password | currentPassword, newPassword (10–64); reemplaza la contraseña y libera la temporal |
| GET | /categories | Categorías públicas |
| GET | /tools | q, categoryId, page (desde 0), size (1–100), mine, sort (`RECENT` por defecto, `PRICE_ASC`, `PRICE_DESC`) |
| GET | /tools/{id} | Detalle de publicación activa |
| POST | /tools | Crear: categoryId, name, description, dailyRate |
| PUT | /tools/{id} | Editar los mismos campos, propietario o administrador |
| DELETE | /tools/{id} | Retirada lógica |
| GET/POST | /tools/{id}/units | Consultar/crear: code, status |
| PUT | /tools/{id}/units/{unitId} | Modificar code y status |
| DELETE | /tools/{id}/units/{unitId} | Retirar unidad |
| POST | /tools/{id}/images | Añadir referencia HTTPS: location |
| POST | /tools/{id}/images/upload | multipart/form-data, campo file, PNG/JPEG hasta 5 MB/16 MP |
| POST | /tools/{id}/images/main | multipart/form-data, campo file: reemplaza la imagen principal (la primera del catálogo) y conserva las demás |
| DELETE | /tools/{id}/images/{imageId} | Eliminar referencia |

## Administración de proveedores

Solo `ADMINISTRADOR`; otro rol recibe 403.

### Solicitudes para ser proveedor

| Método | Ruta | Quién | Operación |
|---|---|---|---|
| POST | /me/provider-application | Cliente | Envía los datos legales (mismo `profile` del alta). 409 si ya tiene una en revisión o el NIT está en uso |
| GET | /me/provider-application | Autenticado | Su solicitud más reciente (204 si no tiene). `needsNotice` indica que aún no vio la respuesta |
| POST | /me/provider-application/acknowledge | Autenticado | Marca la respuesta como vista |
| GET | /me/provider-profile | Proveedor | Sus datos legales, de solo lectura |
| GET | /admin/provider-applications | Administrador | status (PENDIENTE por defecto), page, size; las más antiguas primero |
| POST | /admin/provider-applications/{id}/accept | Administrador | La cuenta pasa a PROVEEDOR con su nombre comercial y sus datos legales |
| POST | /admin/provider-applications/{id}/reject | Administrador | `{"reason": "..."}` obligatorio; el solicitante lo verá |

Tras aceptarse, el token anterior del solicitante todavía dice CLIENTE: la API responde 401 con `code: "ROLE_CHANGED"` (salvo `GET /me` y `POST /auth/refresh`) y la interfaz renueva el token automáticamente. El nombre de un proveedor no se cambia con `PUT /me` (403): es su nombre comercial y lo actualiza el administrador.

| Método | Ruta | Operación |
|---|---|---|
| POST | /admin/providers | email, temporaryPassword (10–64) y profile. 201; 409 si el correo o el NIT existen |
| GET | /admin/providers | page, size. Incluye inactivos y proveedores antiguos con `profile: null` |
| GET | /admin/providers/{id} | Detalle |
| PUT | /admin/providers/{id} | Reemplaza profile (también completa el de un proveedor antiguo) |
| PUT | /admin/providers/{id}/status | `{"active": false}` cierra el acceso de inmediato, también para tokens vigentes |
| GET | /admin/providers/{id}/removal | Vista previa de la eliminación: herramientas activas, solicitudes pendientes, reservas aceptadas por empezar y alquileres en curso. No cambia nada |
| DELETE | /admin/providers/{id} | Elimina el proveedor (irreversible), en una sola transacción: cierra la cuenta para siempre, retira todas sus herramientas del catálogo y cancela con motivo sus solicitudes y reservas que aún no empiezan. Los alquileres en curso siguen. Nada se borra: historial e informes siguen mostrando quién fue. El correo y el NIT quedan reservados |
| PUT | /admin/providers/{id}/password | `{"temporaryPassword": "..."}`; 204 y vuelve a exigir cambio |
| POST | /admin/providers/{id}/logo | Opcional. multipart/form-data, campo file, PNG/JPEG hasta 2 MB; reemplaza el anterior |
| DELETE | /admin/providers/{id}/logo | Quita el logo; la interfaz vuelve al predeterminado (`logoUrl: null`) |

`profile`: personType (`NATURAL` o `JURIDICA`), nit (5–15 dígitos, sin puntos ni DV), verificationDigit (se comprueba con el algoritmo DIAN; si no coincide, 400), legalName, tradeName opcional, legalRepresentative y representativeDocument (obligatorios para jurídica), phone, address, city.

Mientras una cuenta tenga contraseña temporal, cualquier ruta de la API distinta de `GET /me` y `PUT /me/password` responde 403 con `code: "PASSWORD_CHANGE_REQUIRED"`.

```json
{"email":"ventas@obraya.co","temporaryPassword":"temporal-2026","profile":{"personType":"JURIDICA","nit":"800197268","verificationDigit":4,"legalName":"Obras y Equipos SAS","tradeName":"ObraYa","legalRepresentative":"Carlos Pérez","representativeDocument":"CC 79123456","phone":"+57 601 555 1234","address":"Calle 80 # 10-20","city":"Bogotá"}}
```

Inventario requiere rol proveedor o administrador y propiedad del recurso salvo administrador. `mine=true` requiere rol proveedor/administrador. Unidades: `OPERATIVA`, `MANTENIMIENTO`, `RETIRADA`.

Ejemplo de herramienta:

```json
{"categoryId":1,"name":"Taladro 20V","description":"Equipo para perforación","dailyRate":25000}
```

Listado: `{content, totalElements, totalPages, page}`. Creación devuelve 201, baja 204, validación 400, falta de credenciales 401, acceso indebido 403, recurso inexistente 404 y duplicados 409. Los errores de aplicación usan ProblemDetail con `detail`; errores del filtro de seguridad pueden tener cuerpo diferente en este incremento.

La documentación interactiva está en `/swagger-ui.html` (especificación OpenAPI en `/v3/api-docs`), con ejemplos JSON y autenticación JWT mediante el botón **Authorize**. La colección Postman es un entregable pendiente. Las rutas de pagos, facturas, entregas y devoluciones descritas en el desglose todavía no existen.

## Reservas — MVP 02

Fechas ISO `AAAA-MM-DD`, días completos en `America/Bogota`, rango `[start, end)`: el día `end` es la devolución y no se cobra. Total = tarifa diaria × días × cantidad (máximo 90 días y 20 unidades).

| Método | Ruta | Quién | Operación |
|---|---|---|---|
| GET | /tools/{id}/availability | Público | start, end, quantity (1 por defecto); unidades libres y total. No aparta unidades |
| POST | /reservations | Cliente | toolId, start, end, quantity. Crea `SOLICITADA` con la tarifa vigente |
| GET | /reservations | Autenticado | page, size. Cliente: las suyas; proveedor: las de sus herramientas; administrador: todas |
| GET | /reservations/{id} | Cliente dueño, proveedor de la herramienta o administrador | Detalle |
| POST | /reservations/{id}/accept | Proveedor de la herramienta | Asigna unidades concretas y pasa a `ACEPTADA` |
| POST | /reservations/{id}/reject | Proveedor de la herramienta | reason opcional (máximo 500); pasa a `RECHAZADA` |
| DELETE | /reservations/{id} | Cliente dueño o administrador | Cancelación lógica; una aceptada solo antes del día de inicio |
| GET | /reservations/{id}/history | Quien puede ver la reserva | Línea de tiempo: `at`, `action`, `actorRole` (CLIENTE, PROVEEDOR, ADMINISTRADOR o SISTEMA), `actorId`, `actorName`, `detail`, `recorded` |

Cada cambio de estado agrega un evento en `reservation_events` en la misma transacción: quién lo hizo, con qué rol y cuándo. Los eventos nunca se modifican. Los vencimientos los registra `SISTEMA`. Las reservas creadas antes del historial conservan su solicitud (reconstruida con su fecha y su cliente); un cambio posterior sin evento aparece con `recorded: false` y sin fecha ni autor, en lugar de inventarlos.

## Informes — administrador

| Método | Ruta | Operación |
|---|---|---|
| GET | /admin/reports/reservations.csv | from, to (AAAA-MM-DD, incluidas, máximo 366 días), status opcional. Una fila por reserva solicitada en el rango |
| GET | /admin/reports/reservation-events.csv | from, to. Una fila por evento ocurrido en el rango |

CSV para Excel en español: UTF-8 con BOM, separador `;`, coma decimal, fechas y horas en America/Bogota. Los textos que empiezan por `= + - @` se prefijan con `'` para que la hoja de cálculo no los ejecute como fórmula. Incluyen datos personales (nombre y correo de clientes), por eso cada descarga queda en `report_downloads` con el administrador, el informe, el rango y la hora.

Estados: `SOLICITADA` → `ACEPTADA` | `RECHAZADA` | `CANCELADA` | `VENCIDA`; `ACEPTADA` → `CANCELADA`. Solo `ACEPTADA` ocupa unidades. Disponibles = unidades `OPERATIVA` − unidades asignadas a reservas aceptadas que se cruzan con el rango.

Aceptar bloquea la herramienta (`SELECT … FOR UPDATE`) y vuelve a comprobar disponibilidad dentro de la misma transacción: dos aceptaciones simultáneas no pueden asignar la misma unidad; la segunda recibe 409. Una solicitud sin respuesta vence a las 24 horas o al terminar su día de inicio, lo que ocurra primero; una tarea programada la revisa cada 5 minutos (`renttool.reservations.expiry-check`).

Ejemplo de solicitud:

```json
{"toolId":12,"start":"2026-10-20","end":"2026-10-23","quantity":1}
```

Respuesta (201):

```json
{"id":41,"toolId":12,"toolName":"Taladro percutor 800 W","providerId":4,"clientId":7,"clientName":"Laura Gómez","start":"2026-10-20","end":"2026-10-23","days":3,"quantity":1,"dailyRate":25000.00,"total":75000.00,"status":"SOLICITADA","unitIds":[],"requestedAt":"2026-10-15T14:05:00Z","respondBy":"2026-10-16T14:05:00Z","note":null}
```

Errores: 400 fechas inválidas o pasadas, 403 rol o propiedad incorrectos, 404 inexistente, 409 sin unidades libres, estado que no permite la operación o alquiler ya iniciado.
