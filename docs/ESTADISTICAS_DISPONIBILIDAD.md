# Estadísticas y búsqueda por disponibilidad

## Uso
- En el catálogo, selecciona entrega, devolución y cantidad (1 a 20); pulsa Buscar. Se combina con texto, categoría, proveedor y precio. Quitar fechas restaura la consulta habitual.
- Al abrir una herramienta, se conservan las fechas y cantidad elegidas. La reserva se vuelve a validar en el servidor; buscar no aparta unidades.
- En /admin y /proveedor, abre **Estadísticas de reservas**, elige fechas y pulsa Consultar. El administrador ve toda la plataforma; el proveedor, solo reservas de sus herramientas, incluidas las retiradas. El cliente no tiene acceso.

## Cómo interpretar el resumen
El rango incluye ambos días y usa America/Bogota. Incluye reservas **solicitadas en esas fechas**, hasta 366 días, y muestra su estado actual:
- Total y desglose por estado de reserva.
- Reservas con pago aprobado y suma de esos pagos de demostración, una vez por reserva. Los pagos rechazados no suman. No es una contabilidad de ingresos por fecha de pago.
- Entregadas/en uso, devueltas, y entregadas sin devolver cuya fecha de devolución es hoy o anterior.
- Incidencias que siguen abiertas para esas reservas.
- Las cinco herramientas más solicitadas, por número de solicitudes, con unidades solicitadas; incluye todos los estados.

Un resumen vacío muestra ceros. Cambiar las fechas actualiza el resumen; las acciones de reservas también lo actualizan cuando está abierto.

## API
GET /api/v1/tools?start=2026-11-01&end=2026-11-03&quantity=2&q=taladro&sort=PRICE_ASC&page=0&size=6
Consulta pública. Fechas opcionales, pero deben venir juntas; cantidad distinta de 1 requiere fechas. Rango máximo 90 días y sin fechas pasadas. Mantiene el formato paginado del catálogo. Filtra antes de paginar para que el total y las páginas sean correctos.

GET /api/v1/statistics?from=2026-11-01&to=2026-11-30
Requiere JWT de administrador o proveedor. No admite escoger otro proveedor ni devuelve datos personales del cliente.

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
