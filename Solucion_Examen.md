### SOLUCION EXAMEN SANTIAGO VILLAMIZAR MANTILLA

## Problematica

Implementar el flujo de devolución de una herramienta, permitiendo al proveedor marcar la devolución como completada y, opcionalmente, registrar un reporte de daños.



Descripción: Este examen cubre el final del ciclo de vida del alquiler. El proveedor debe inspeccionar el equipo devuelto y actualizar el sistema, lo cual es vital para la "Gestión de devoluciones" y para "Administrar reportes de daños".



Instrucciones:

Nueva Entidad ReporteDano:
Crea una entidad ReporteDano que se relacione con Reserva (OneToOne o ManyToOne).
Campos: descripcion (String), costoReparacion (Double), fechaReporte (LocalDate/Timestamp).



Modificar Entidad Reserva:
Añade un campo para el estado de la devolución (ej. EstadoDevolucion como Enum): PENDIENTE_DEVOLUCION, DEVUELTO_OK, DEVUELTO_CON_DAÑOS.
El estado inicial debe ser PENDIENTE_DEVOLUCION (asumiendo que la reserva ya fue APROBADA y está en curso).



Crear un DevolucionDTO:
Este DTO recibirá la información del formulario de devolución.
Campos: estado (String: "OK" o "DAÑOS"), descripcionDano (String, opcional), costoReparacion (Double, opcional).



Lógica en DevolucionService (o ReservaService):
Implementa un método registrarDevolucion(Long idReserva, DevolucionDTO dto).
Busca la reserva.
Si dto.estado es "OK":
Cambia el estado de la Reserva a DEVUELTO_OK.
Si dto.estado es "DAÑOS":
Cambia el estado de la Reserva a DEVUELTO_CON_DAÑOS.
Crea y guarda un nuevo objeto ReporteDano usando los datos del DTO y asociándolo a la reserva.



Controlador REST (Nuevo Endpoint):
POST /api/reservas/{id}/registrar-devolucion (Rol: Proveedor): Recibe el DevolucionDTO en el body y llama al servicio.	



## Solucion

En el programa esta implementado un apartado llamado "Entrega y devolucion" para el usuario PROVEEDOR (Usuario PROVEEDOR test "midnight@renttool.demo" contraseña implementada en el .env creada al momento de ejecutar la aplicacion).

Este apartado aparece al momento que se acerca la fecha limite de entrega y despliega un apartado visual que permite reportar incidencias (DAÑOS), multas al momento del retardo de la entrega cargos adicionales por perdida del equipo prestado y observaciones adicionales de la devolucion. 

Si no se marca con alguna incidencia es que el producto llego perfecto y sin daños y esta OK.





## SOLUCION APARTADO POR APARTADO

# Necesidad: Devolucion de herramienta:
Implementar el flujo de devolución de una herramienta, permitiendo al proveedor marcar la devolución como completada y, opcionalmente, registrar un reporte de daños.

<a href="https://ibb.co/39MyQV2B"><img src="https://i.ibb.co/21s7HRxg/Captura-desde-2026-10-26-07-19-41.png" alt="Captura-desde-2026-10-26-07-19-41" border="0"></a>

# boton:

<a href="https://imgbb.com/"><img src="https://i.ibb.co/8L6s4YGy/Captura-desde-2026-10-26-07-24-22.png" alt="Captura desde 2026 10 26 07 24 22" border="0"></a>



# Necesidad: Reporte de daño

Nueva Entidad ReporteDano:
Crea una entidad ReporteDano que se relacione con Reserva (OneToOne o ManyToOne).
Campos: descripcion (String), costoReparacion (Double), fechaReporte (LocalDate/Timestamp).



### Lugar donde se encuentra:

<a href="https://ibb.co/WNL7CzRn"><img src="https://i.ibb.co/hxSr0dTs/Captura-desde-2026-10-26-07-25-27.png" alt="Captura-desde-2026-10-26-07-25-27" border="0"></a>

<a href="https://imgbb.com/"><img src="https://i.ibb.co/1fbg9QNK/Captura-desde-2026-10-26-07-27-24.png" alt="Captura desde 2026 10 26 07 27 24" border="0"></a>

Al momento de dar click se despliega este apartado:

<a href="https://ibb.co/wV5qP6x"><img src="https://i.ibb.co/Z5qrFTk/Captura-desde-2026-10-26-07-28-10.png" alt="Captura-desde-2026-10-26-07-28-10" border="0"></a>

Pregunta si fue un DAÑO (Requerimiento del examen) o si se perdio (extra) junto con CUAL unidad y una breve descripcion:

<a href="https://ibb.co/Z6Lzn19Y"><img src="https://i.ibb.co/3yrmDYKM/Captura-desde-2026-10-26-07-28-37.png" alt="Captura-desde-2026-10-26-07-28-37" border="0"></a>

Y se registra el tiempo de la insidencia en el HISTORIAL que fue el momento en el que se completo este formulario de daños y devolucion:

<a href="https://ibb.co/3m9V4H8f"><img src="https://i.ibb.co/LdXBYfqQ/Captura-desde-2026-10-26-07-31-08.png" alt="Captura-desde-2026-10-26-07-31-08" border="0"></a>



## Necesidad: "Devuelto OK":

Modificar Entidad Reserva:
Añade un campo para el estado de la devolución (ej. EstadoDevolucion como Enum): PENDIENTE_DEVOLUCION, DEVUELTO_OK, DEVUELTO_CON_DAÑOS.
El estado inicial debe ser PENDIENTE_DEVOLUCION (asumiendo que la reserva ya fue APROBADA y está en curso).

Si en el apartado de insidencia NO SE REGISTRA NINGUNA el programa lo va a tomar como que la herramienta devuelta esta OK y sin ningun daño, solo se reportaran con daños al momento de registrar la insidencia y si el daño fue notado despues de marcarlo como recibido aun se pueden hacer reportes de DAÑOS.



