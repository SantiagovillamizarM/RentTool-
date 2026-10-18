# Entregas, devoluciones e incidencias

## Recorrido
1. El cliente solicita una reserva y el proveedor la acepta.
2. El cliente registra el pago de demostración aprobado.
3. En «Entrega y devolución», el proveedor registra la entrega y sus observaciones.
4. Al recibir el equipo, revisa todas las unidades y elige: buen estado, dañada o perdida.
5. RentTool registra la devolución, actualiza inventario y crea incidencias por daños o pérdidas.
6. El proveedor o administrador resuelve las incidencias indicando qué se hizo.

El cliente consulta sus propios registros. Solo el proveedor dueño de la herramienta o un administrador puede registrar y resolver movimientos.
El administrador cuenta con «Supervisar incidencias abiertas» en la sección de reservas, con paginación.

## Reglas
- La entrega requiere reserva aceptada, pago aprobado y estar dentro del periodo del alquiler.
- Las unidades asignadas deben estar operativas, sin daños abiertos y sin otra entrega pendiente.
- No se permite una segunda entrega ni registrar una devolución antes de la entrega.
- La devolución es completa: se debe indicar exactamente una vez cada unidad entregada.
- Las observaciones son obligatorias para unidades dañadas o perdidas.
- Buen estado: se habilita salvo retirada previa o incidencia abierta.
- Dañada: pasa a mantenimiento; perdida: pasa a retirada.
- Una entrega pendiente mantiene las unidades ocupadas, también después de la fecha prevista.
- Una devolución anticipada libera sus unidades en buen estado.
- No se permite aumentar manualmente una unidad a OPERATIVA si tiene daño o pérdida sin resolver.
- Resolver conserva el reporte original, autor, fecha y observaciones de resolución.
- «Equipo revisado y listo para volver a usarse» habilita solo una unidad devuelta y sin otras incidencias de daño o pérdida.
- Resolver sin esa opción conserva el estado de mantenimiento o retirada.
- Los registros de entrega y devolución conservan fecha y usuario responsable.

Para probar la entrega desde la interfaz, crea una reserva que comience hoy, acéptala y registra el pago de demostración.

## Estados y compatibilidad
La solicitud de reserva mantiene sus estados anteriores (SOLICITADA, ACEPTADA, etc.).
El movimiento físico se consulta aparte: PENDIENTE_ENTREGA, EN_USO o DEVUELTA.
La API de reservas incluye rentalStatus, y las tarjetas muestran «Entregada / en uso» o «Devuelta».
Así no se modifican migraciones anteriores ni se pierde historial de reservas o facturas.
Las reservas devueltas dejan de incluirse como abiertas al ajustar inventario o dar de baja proveedores.
Las incidencias se consultan en «Entrega y devolución»; el historial anterior sigue mostrando las decisiones de la reserva.

## Organización
- domain/operations: RentalOperation, UnitReturn e Incident, sin Spring.
- application/operations: casos de uso, repositorio abstracto y RentalService.
- infrastructure/operations: JPA, transacciones y controladores REST.
- frontend/operations.js: formularios compartidos por los tres roles.
- V12__deliveries_returns_incidents.sql: tablas rental_operations, rental_returns e incidents.

Las escrituras usan el mismo bloqueo de reserva/herramienta que la asignación de unidades.
Si una devolución falla, ni el registro ni el estado de las unidades se guardan parcialmente.

## API
Todas las rutas requieren JWT:
- GET /api/v1/reservations/{id}/operations
- POST /api/v1/reservations/{id}/delivery con {"notes":"Con accesorios"}
- POST /api/v1/reservations/{id}/return con {"notes":"Revisión completa","units":[{"unitId":1,"condition":"BUENO","notes":""}]}
- POST /api/v1/reservations/{id}/incidents con {"unitId":1,"type":"DANO","description":"Cable roto"}
- POST /api/v1/incidents/{id}/resolve con {"resolution":"Cable reemplazado y probado","readyToUse":true}
- GET /api/v1/incidents?page=0&size=20 (solo administrador)

Tipos: DANO, PERDIDA, OTRO. OTRO admite no indicar unidad.
Condiciones de devolución: BUENO, DANADO, PERDIDO.

## Límites
No hay devoluciones parciales, cobros por daños, multas automáticas ni envío físico integrado.
La resolución de una incidencia es una revisión registrada por el proveedor o administrador; no borra lo sucedido.
