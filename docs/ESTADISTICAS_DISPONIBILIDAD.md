# Estadísticas y búsqueda por disponibilidad

## Uso
- En el catálogo, selecciona entrega, devolución y cantidad (1 a 20); pulsa Buscar. Se combina con texto, categoría, proveedor y precio. Quitar fechas restaura la consulta habitual.
- Al abrir una herramienta, se conservan las fechas y cantidad elegidas. La reserva se vuelve a validar en el servidor; buscar no aparta unidades.
- En /proveedor, abre **Estadísticas de reservas**, elige fechas y pulsa Consultar. Cada proveedor ve solo las reservas de sus herramientas, incluidas las retiradas; las de un proveedor no aparecen en las de otro. El administrador y el cliente no tienen acceso.

## Cómo interpretar el resumen
El rango incluye ambos días y usa America/Bogota. Incluye reservas **solicitadas en esas fechas**, hasta 366 días, y muestra su estado actual:
- Total y desglose por estado: solicitadas (pendientes), aceptadas, rechazadas y canceladas. Las vencidas cuentan en el total pero no se listan aparte.
- **Ventas**: reservas aceptadas con pago aprobado y no reembolsado. Es la misma definición que usa la comparación del administrador. No es una contabilidad de ingresos por fecha de pago.
- **Pagadas por aceptar**: lo que ya pagaron tus clientes en solicitudes que todavía no aceptas.
- **Entregadas / en uso**: entregas registradas (automáticas el día de inicio) que aún no se devuelven.
- Incidencias que siguen abiertas para esas reservas, incluidas las que reporta el cliente.
- Las cinco herramientas más solicitadas, por número de solicitudes, con unidades solicitadas; incluye todos los estados.

Un resumen vacío muestra ceros. Cambiar las fechas actualiza el resumen; las acciones de reservas también lo actualizan cuando está abierto.

## Rendimiento de proveedores (administrador)
En /admin, pestaña Proveedores, el apartado **Rendimiento de proveedores** compara a todos los proveedores en un rango de fechas de solicitud (por defecto, los últimos 30 días). Venta es una reserva aceptada con pago aprobado y no reembolsado, igual que en el panel de cada proveedor.
- Tarjetas: ventas totales, promedio de ventas por proveedor, aceptación promedio de la plataforma y número de proveedores para citar.
- Gráfica de barras con las ventas de cada proveedor y una línea con el promedio. Al pasar el cursor o enfocar una barra se ven solicitudes, aceptadas y tasa de aceptación.
- Tabla con contacto (correo, teléfono y ciudad), alertas, herramientas activas, solicitudes, aceptadas, rechazadas, vencidas sin respuesta, pendientes, aceptación, ventas, venta promedio y unidades alquiladas. Se puede filtrar para ver solo los proveedores con alertas.

La aceptación se calcula sobre las solicitudes que dependían del proveedor: aceptadas, rechazadas o vencidas. Los promedios y las alertas solo consideran proveedores activos. Un proveedor recibe una alerta si:
- Sus ventas están por debajo de la mitad del promedio.
- Dejó vencer sin respuesta el 25 % o más de sus solicitudes.
- Acepta menos del 50 % con al menos 3 solicitudes respondidas.
- No tiene herramientas publicadas, o no recibió solicitudes en el período.

## API
GET /api/v1/tools?start=2026-11-01&end=2026-11-03&quantity=2&q=taladro&sort=PRICE_ASC&page=0&size=6
Consulta pública. Fechas opcionales, pero deben venir juntas; cantidad distinta de 1 requiere fechas. Rango máximo 90 días y sin fechas pasadas. Mantiene el formato paginado del catálogo. Filtra antes de paginar para que el total y las páginas sean correctos.

GET /api/v1/admin/provider-performance?from=2026-11-01&to=2026-11-30
Solo administrador. Devuelve los totales, los promedios y una fila por proveedor con sus alertas.

GET /api/v1/statistics?from=2026-11-01&to=2026-11-30
Requiere JWT de proveedor; el administrador recibe 403. No admite escoger otro proveedor ni devuelve datos personales del cliente.

## Estructura
Se conservan los módulos Maven domain, application e infrastructure:
- application/discovery: puerto de entrada y servicio que coordinan catálogo y disponibilidad existente.
- application/insights: puerto de entrada y servicio de estadísticas que usan puertos de salida existentes.
- infrastructure/insights: configuración de dependencias y controlador HTTP; lectura transaccional del resumen.
- frontend/statistics.js: panel compartido; app.js incorpora los filtros del catálogo.

No necesita nuevas tablas ni migraciones. Para mantenerlo comprensible, las consultas combinan datos mediante los puertos existentes. En un catálogo grande convendría pasar el filtrado y las sumas a consultas agrupadas; esta versión está orientada al tamaño de la demostración.

## Correcciones y comprobación
Se impide que respuestas anteriores del catálogo reemplacen filtros recientes. Al cambiar fechas o cantidad de una reserva se desactiva el botón mientras se consulta disponibilidad y se invalidan respuestas anteriores. Al cerrar el detalle se cancela el temporizador pendiente.

Las pruebas de API cubren filtros antes de paginar, límites de fechas, reservas superpuestas, límite de devolución exclusivo, permisos, aislamiento entre proveedores, pagos aprobados sin duplicación y devoluciones con daños.
