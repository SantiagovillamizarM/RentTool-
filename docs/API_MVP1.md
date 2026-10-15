# API implementada — MVP 01

Base `/api/v1`, JSON excepto subida de archivos. Autenticación: `Authorization: Bearer <token>` obtenido por registro/login. Rol público permitido: `CLIENTE` o `PROVEEDOR`.

| Método | Ruta | Operación |
|---|---|---|
| POST | /auth/register | name, email, password (10–64 caracteres), role |
| POST | /auth/login | email, password; devuelve token y user |
| GET/PUT | /me | Perfil propio; PUT modifica name |
| GET | /categories | Categorías públicas |
| GET | /tools | q, categoryId, page (desde 0), size (1–100), mine |
| GET | /tools/{id} | Detalle de publicación activa |
| POST | /tools | Crear: categoryId, name, description, dailyRate |
| PUT | /tools/{id} | Editar los mismos campos, propietario o administrador |
| DELETE | /tools/{id} | Retirada lógica |
| GET/POST | /tools/{id}/units | Consultar/crear: code, status |
| PUT | /tools/{id}/units/{unitId} | Modificar code y status |
| DELETE | /tools/{id}/units/{unitId} | Retirar unidad |
| POST | /tools/{id}/images | Añadir referencia HTTPS: location |
| POST | /tools/{id}/images/upload | multipart/form-data, campo file, PNG/JPEG hasta 5 MB/16 MP |
| DELETE | /tools/{id}/images/{imageId} | Eliminar referencia |

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
