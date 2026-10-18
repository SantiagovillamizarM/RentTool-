# Pagos y facturas de demostración

Esta función simula un pago para explicar el flujo del proyecto escolar.
No hay pasarela externa, dinero real, credenciales especiales ni datos bancarios.

## Cómo probarlo
1. Un cliente solicita una reserva y el proveedor la acepta.
2. El cliente abre «Pagos y factura».
3. Elige «Pago aprobado» o «Pago rechazado» y confirma la simulación.
4. Si se aprueba, se registra el pago y se crea la factura automáticamente.
5. Si se rechaza, se guarda el intento y el cliente puede intentarlo otra vez.
6. Cliente, proveedor de la reserva y administrador pueden consultar los pagos y descargar la factura.

La factura es HTML descargable, con los datos históricos del alquiler.
Se puede guardar como PDF desde Imprimir → Guardar como PDF.
Está marcada como demostración, sin cobro real y sin validez fiscal.

## Organización del código
- domain/billing: modelos Payment e Invoice.
- application/billing: casos de uso y servicio PaymentService.
- infrastructure/billing: controladores, repositorios JPA y configuración.
- frontend/billing.js: formulario de simulación y descarga.

Se mantienen los tres módulos Maven que aparecen en Java Projects.
El servicio obtiene el importe de la reserva; el navegador solo envía el resultado que desea simular.
El bloqueo de la reserva evita duplicar pagos si se confirma dos veces.
Pago aprobado y factura se guardan juntos en una transacción.
Una reserva con pago aprobado no se puede cancelar en esta versión.

## API
- POST /api/v1/reservations/{id}/payments con {"result":"APROBADO"} o {"result":"RECHAZADO"}.
- GET /api/v1/reservations/{id}/payments para consultar historial.
- GET /api/v1/reservations/{id}/invoice para descargar la factura.

Todas las rutas requieren autenticación. Solo el cliente dueño de una reserva aceptada puede simular el pago.
Una segunda aprobación devuelve el pago existente y no crea otra factura.

## Base de datos
La migración V11 conserva las tablas payments e invoices.
No se modifica una migración que ya podría haberse aplicado en tu PC.
Algunos estados y la columna transaction_id se conservan por compatibilidad; la simulación nueva solo genera APPROVED y DECLINED, sin identificador externo.

## Alcance
Esto cubre la demostración académica del flujo de pagos y facturas.
El requisito de pagos en línea con dinero real sigue pendiente si el instructor lo exige.
