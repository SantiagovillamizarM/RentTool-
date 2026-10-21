# Pagos y facturas de demostración

Esta función simula una pasarela de pagos para explicar el flujo del proyecto escolar.
No hay pasarela externa ni dinero real. **No se deben escribir datos de una tarjeta real**: solo se aceptan las tarjetas de prueba.

## Flujo
1. El cliente solicita una reserva y, a continuación, se abre **Pagos y factura** para pagarla.
2. Paga con tarjeta o Nequi de prueba. Si se aprueba, se registra el pago y se crea la factura automáticamente. Si se rechaza, se guarda el intento con el motivo y el cliente puede intentarlo otra vez.
3. El proveedor llama al cliente, confirma la entrega y acepta. **Solo se puede aceptar una reserva pagada.**
4. Si el proveedor la rechaza, no responde a tiempo, el cliente la cancela o el proveedor sale de la plataforma, el pago se **reembolsa** (simulado) y la factura queda **anulada** con la fecha y el motivo.
5. Cliente, proveedor de la reserva y administrador pueden consultar los pagos y descargar la factura.

## Datos de prueba
| Método | Dato | Resultado |
|---|---|---|
| Tarjeta | 4242 4242 4242 4242 · 5555 5555 5555 4444 | Aprobada |
| Tarjeta | 4000 0000 0000 0002 | Rechazada: fondos insuficientes |
| Tarjeta | 4000 0000 0000 0127 | Rechazada por el banco |
| Nequi | Cualquier celular de 10 dígitos que empiece por 3 | Aprobado |
| Nequi | 399 222 2222 | Rechazado |

La tarjeta también se valida como lo haría un banco: dígito de control (Luhn), fecha de vencimiento futura, CVV de 3 o 4 dígitos y nombre del titular. Una tarjeta válida que no sea de prueba se rechaza.

## Qué se guarda
Solo el método, un detalle enmascarado (por ejemplo «Visa •••• 4242» o «Nequi •••• 4567»), el resultado y su motivo. **Nunca se guardan el número completo ni el CVV**, que es la práctica que exige la norma de seguridad de tarjetas (PCI DSS). El navegador borra los datos de la tarjeta después de enviarlos.

La factura es HTML descargable, con los datos históricos del alquiler y el medio de pago. Se puede guardar como PDF desde Imprimir → Guardar como PDF. Está marcada como demostración, sin cobro real y sin validez fiscal.

## Organización del código
- domain/billing: Payment, Invoice y SimulatedGateway (validación y resultado de la pasarela).
- application/billing: casos de uso y PaymentService (pago, comprobación antes de aceptar y reembolso).
- infrastructure/billing: controladores, repositorios JPA y configuración.
- infrastructure/reservation/config/TransactionalReservations: exige el pago al aceptar y reembolsa al rechazar, cancelar o vencer, en la misma transacción.
- frontend/billing.js: pasarela simulada, comprobante y descarga de la factura.

El servicio obtiene el importe de la reserva; el navegador nunca envía el valor a cobrar. El bloqueo de la reserva evita duplicar pagos si se confirma dos veces: una segunda aprobación devuelve el pago existente y no crea otra factura.

## API
- POST /api/v1/reservations/{id}/payments con `{"method":"TARJETA","number":"4242 4242 4242 4242","holder":"ANA RUIZ","expMonth":12,"expYear":2030,"cvv":"123"}` o `{"method":"NEQUI","phone":"3001234567"}`.
- GET /api/v1/reservations/{id}/payments para consultar el historial, el total y si se puede pagar.
- GET /api/v1/reservations/{id}/invoice para descargar la factura.
- Cada reserva incluye `paid`: si tiene un pago aprobado que no se ha reembolsado.

Todas las rutas requieren autenticación. Solo el cliente dueño de una reserva pendiente (o aceptada sin pago, en datos anteriores a este flujo) puede pagar, antes de la fecha de inicio.

## Base de datos
La migración V11 crea las tablas payments e invoices. La V13 agrega a payments las columnas method, detail y message. Un reembolso cambia el pago a VOIDED y anota la anulación en la copia guardada de la factura.

## Alcance
Esto cubre la demostración académica. Una integración real usaría una pasarela como Wompi, PayU o Mercado Pago: la página nunca recibiría los datos de la tarjeta, sino que redirigiría a la pasarela y esta avisaría del resultado con un webhook.
