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

OpenAPI/Swagger interactivo y colección Postman son entregables pendientes. Las rutas de los MVP siguientes descritas en el desglose todavía no existen.
