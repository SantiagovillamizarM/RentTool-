# Planeación paso a paso de RentTool

Estado actual: primer MVP implementado y verificado. Los pasos 2–7 siguen pendientes; consultar `docs/VALIDACION.md` y README para las comprobaciones y límites del incremento.

## Base de la planeación

Fuentes: `DESGLOSE_PROYECTO_IA.md`, `ejercicioDemo.md` y `prompt.txt`. La solicitud actual es planear el proyecto: la instrucción de `prompt.txt` de generar código se toma como referencia de diseño y funcionalidades del frontend, no como una orden de comenzar a programar en esta etapa.

Se mantiene **Java 21 y Spring Boot exactamente 4.1.1**. Arquitectura hexagonal, DDD, Maven multimódulo, MySQL con justificación de 4FN, Spring Security y JWT. Propuesta de frontend: HTML5, CSS3 y JavaScript con Fetch API; el estilo de Tailwind puede reproducirse con CSS propio sin añadir esa dependencia. Toda integración usa `/api/v1`.

Los MVP son incrementales: cada paso conserva lo anterior y añade un flujo verificable desde la interfaz hasta la base de datos. Las reglas propuestas del desglose siguen siendo propuestas, no requisitos originales del ejercicio. No se asignan fechas hasta conocer disponibilidad y tamaño del equipo.

## Paso 1: terminar el primer MVP — acceso, inventario y catálogo funcional

**Resultado:** un proveedor puede registrarse y publicar herramientas; un cliente puede registrarse, iniciar sesión y consultar esas herramientas desde una interfaz funcional.

Orden de trabajo:

1. Crear el proyecto Maven multimódulo, fijar Java 21/Boot 4.1.1 y separar dominio, aplicación y adaptadores. Preparar configuración por entorno y conexión a MySQL.
2. Diseñar el modelo relacional global y documentar su estrategia de 4FN; implementar las primeras migraciones de usuarios, perfiles, categorías, herramientas, imágenes y unidades.
3. Implementar registro de cliente/proveedor, login JWT, perfil, validación de correo único y contraseñas con hash. Preparar creación controlada del administrador.
4. Aplicar restricciones por rol y propietario, errores uniformes y CORS. Elegir y documentar almacenamiento de JWT y cierre de sesión.
5. Implementar inventario del proveedor: crear, editar, consultar y retirar herramientas; tarifa diaria, categoría, descripción, imágenes y unidades físicas.
6. Implementar catálogo público con paginación, búsqueda y filtro por categoría; detalle de herramienta y tarifa en COP.
7. Construir `index.html`, `styles.css` y `app.js`, junto con las vistas de acceso, catálogo y panel de inventario. Conectar los formularios y listados a la API real.
8. Documentar instalación local, endpoints incluidos y recorrido de demostración; verificar el flujo completo.

Diseño que se entrega desde este MVP, basado en `prompt.txt`:

- Fondo `#0A0A0B`, tarjetas `#16161A`, acento `#FFD700`, títulos blancos y texto secundario `#A0A0AB`.
- Tarjetas con radios de 12–16 px, bordes sutiles y brillo amarillo moderado al interactuar.
- Navbar con Inicio, Catálogo, Cómo funciona, Preguntas frecuentes, Contacto y botones de acceso.
- Hero con “Potencia tus obras con el mejor equipamiento”, llamadas a la acción y herramienta destacada.
- Categorías, tarjetas de productos y secciones informativas con navegación funcional.
- Diseño adaptable, etiquetas accesibles y estados de carga, vacío, error y éxito.
- Contacto muestra información configurada; no implica un sistema de envío de correos.

**Criterios para terminar:**

- Desde una instalación limpia, backend y frontend arrancan siguiendo README.
- Un proveedor publica una herramienta con imagen y unidades y aparece en el catálogo sin modificar código.
- Un cliente inicia sesión, consulta catálogo y detalle, pero recibe 403 si intenta modificar inventario.
- Un proveedor no puede editar herramientas ajenas; no se exponen contraseñas ni secretos.
- El botón de reserva informa que el flujo estará disponible en el siguiente MVP; no muestra una reserva ficticia.
- Las métricas comerciales del prompt, como “99%” o “+5.000 equipos”, no se muestran como hechos sin respaldo. En esta etapa se omiten o se sustituyen por conteos reales de catálogo.

**Todavía pendiente:** disponibilidad por fechas, reservas, pagos, facturas, entregas y devoluciones.

## Paso 2: terminar el segundo MVP — solicitudes y gestión de reservas

**Depende de:** paso 1.

**Resultado:** un cliente solicita herramientas para fechas concretas y el proveedor acepta o rechaza sin asignar dos veces la misma unidad.

Orden de trabajo:

1. Implementar reservas y asignaciones de unidades, estados y reglas de fechas del desglose.
2. Calcular disponibilidad por rango y cantidad, excluyendo mantenimiento y ocupaciones superpuestas.
3. Crear solicitud con `toolId`, `quantity`, `startDate`, `endDate` y `deliveryMode`; calcular importe en servidor y conservar tarifa pactada.
4. Añadir edición mientras está solicitada, aceptación/rechazo del proveedor, cancelación previa al pago e historial por propietario.
5. Implementar bloqueo transaccional al aceptar, plazo configurable de pago y vencimiento que libere unidades.
6. Conectar filtros por fechas, formulario de reserva, “Mis reservas” y gestión de solicitudes del proveedor.
7. Documentar contrato JSON y verificar concurrencia contra MySQL.

**Criterios para terminar:**

- El cliente solicita dos unidades, ve el precio calculado y el proveedor ve la solicitud.
- Aceptar asigna unidades; rechazar registra motivo; cancelar o vencer libera la asignación.
- Dos aceptaciones concurrentes no reservan la misma última unidad para fechas superpuestas.
- Fechas inválidas se rechazan y cambios posteriores del catálogo no alteran el precio pactado.
- La reserva aceptada se muestra como pendiente de pago, sin declararse confirmada.

**Todavía pendiente:** confirmar mediante pago, emitir factura y entregar equipos.

## Paso 3: terminar el tercer MVP — pagos de demostración y facturas

**Depende de:** paso 2.

**Resultado:** se puede demostrar solicitud → aceptación → pago verificado → confirmación → factura descargable.

Orden de trabajo:

1. Implementar puerto de pasarela y adaptador simulado explícito para el entorno demo.
2. Crear intentos de pago desde el importe de la reserva; registrar resultado e historial.
3. Confirmar reserva solo al verificar un pago aprobado, con idempotencia y control del vencimiento.
4. Generar una factura académica por reserva pagada, con datos históricos y PDF; permitir consultas por rol y pertenencia.
5. Implementar reintentos de facturación mediante outbox o mecanismo persistente equivalente.
6. Añadir cancelación después del pago y registro de reembolso con estado independiente.
7. Construir pantalla de pago, historial, descarga de factura y consulta del proveedor.

**Criterios para terminar:**

- Pago aprobado confirma; pago fallido no confirma; repetir la notificación no duplica efectos.
- Un pago tardío no reactiva una reserva vencida ni toma unidades ya reasignadas.
- Fallar al producir el PDF permite reintentar sin emitir dos facturas.
- Cliente y proveedor solo consultan sus pagos y documentos.
- El frontend identifica la simulación de forma visible; no se presenta como una integración real ni como factura fiscal certificada.

**Todavía pendiente:** pasarela externa, entrega/devolución y supervisión completa.

## Paso 4: terminar el cuarto MVP — operación completa del alquiler

**Depende de:** paso 3.

**Resultado:** proveedor y cliente pueden completar el recorrido hasta la devolución y conservar el historial del equipo.

Orden de trabajo:

1. Programar recogida o entrega con fecha prevista y dirección cuando corresponda.
2. Registrar entrega efectiva y condición por unidad, únicamente para reservas confirmadas.
3. Registrar devolución completa, fecha real, condición y observaciones.
4. Crear incidencias de daño con evidencias y retirar de disponibilidad las unidades afectadas.
5. Gestionar mantenimiento, recuperación de condición operativa y conflictos con reservas futuras.
6. Mostrar seguimiento e historial en paneles del proveedor y cliente.

**Criterios para terminar:**

- Se demuestra registro → publicación → reserva → aceptación → pago demo → factura → entrega → devolución.
- No se permite entregar sin confirmar ni devolver sin entrega previa.
- Una unidad dañada queda en mantenimiento y no aparece disponible para nuevas asignaciones.
- Las devoluciones tardías quedan registradas y se identifican reservas futuras afectadas.
- No se aplican multas ni cobros por daños sin una política definida.

## Paso 5: terminar el quinto MVP — administración y reportes

**Depende de:** paso 4.

**Resultado:** el administrador supervisa la plataforma y el proveedor consulta las cifras de su negocio.

Orden de trabajo:

1. Completar gestión administrativa de clientes y proveedores: registro, consulta, edición, activación y baja lógica.
2. Añadir supervisión global de herramientas, alquileres, pagos, facturas, entregas y devoluciones.
3. Implementar revisión y resolución de incidencias con auditoría.
4. Implementar registro de costos operativos e informes de ingresos netos y rentabilidad.
5. Añadir herramientas más alquiladas, clientes frecuentes y disponibilidad/estado de inventario por periodo.
6. Construir panel administrativo y reportes del proveedor con filtros y fórmulas documentadas.
7. Incorporar métricas reales a la página principal cuando exista una fuente adecuada; no prometer garantías ni tiempos de entrega inexistentes.

**Criterios para terminar:**

- Desactivar un usuario impide operar y conserva el historial.
- Administrador ve datos globales; proveedor solo los de su operación.
- Reportes coinciden con un conjunto conocido de pagos, reembolsos, alquileres y costos.
- Sin datos de costos no se presenta el ingreso como rentabilidad.
- Resolver incidencias deja trazabilidad y no rehabilita automáticamente equipos sin inspección.

## Paso 6: completar pagos reales en entorno sandbox

**Depende de:** paso 3; se propone después del paso 5 para validar primero el flujo completo con simulación.

**Resultado:** el pago se realiza en el entorno de prueba de una pasarela externa, manteniendo las mismas reglas de reservas y facturación.

Orden de trabajo:

1. Seleccionar pasarela y obtener credenciales sandbox; definir política de reembolsos y configuración necesaria.
2. Implementar el adaptador real sin modificar las reglas de dominio.
3. Validar firma/autenticidad de webhooks, importes, moneda y referencias; añadir conciliación y reintentos.
4. Integrar checkout y resultados de pago en el frontend sin confiar en el retorno del navegador.
5. Probar aprobación, fallo, duplicados, eventos fuera de orden, vencimiento y reembolso en sandbox.

**Criterios para terminar:** una operación sandbox completa confirma una reserva una sola vez, genera una factura y puede conciliarse o reembolsarse con evidencia de la pasarela.

**Dependencia externa:** sin proveedor y credenciales puede completarse el contrato y la simulación, pero este paso queda pendiente. El simulador no satisface por sí solo el requisito final de pagos reales en línea. No se habilitan cobros de producción como parte de esta planeación.

## Paso 7: terminar la versión de entrega — pruebas, documentación y despliegue reproducible

**Depende de:** pasos anteriores; si falta el paso 6, identificar la entrega como demostración con pagos simulados.

Orden de trabajo:

1. Completar scripts SQL y datos coherentes con al menos 20 registros por entidad de negocio; justificar catálogos finitos y tablas técnicas según el desglose.
2. Finalizar diccionario de datos, demostración de 4FN y diagrama relacional en fuente e imagen.
3. Completar OpenAPI/Swagger con ejemplos, JWT, errores y transiciones; entregar colección Postman.
4. Ejecutar pruebas unitarias, integración MySQL, concurrencia, permisos y flujo completo desde navegador.
5. Verificar diseño en móvil, tableta y escritorio, navegación por teclado y todos los estados de interfaz.
6. Completar README, guía de compilación y guía de despliegue con versiones exactas y variables de entorno.
7. Preparar Docker Compose, persistencia de MySQL y ejecución del JAR con Tomcat embebido; comprobar instalación desde cero.
8. Preparar código y documentación para el repositorio privado solicitado por el ejercicio. Su publicación y acceso del instructor se gestionan cuando se soliciten.

**Criterios para terminar:** otra persona puede instalar, ejecutar, probar y demostrar todos los roles usando únicamente la documentación; no hay secretos en archivos versionados; las integraciones pendientes están identificadas.

## Seguimiento y definición común de terminado

Cada paso se desarrolla en orden: modelo y migración → dominio → caso de uso → adaptadores/API → interfaz → comprobaciones y documentación. Las pruebas y documentación se actualizan en cada MVP; el último paso consolida la entrega.

Una función termina cuando persiste datos reales, aplica permisos en backend, muestra errores útiles en frontend y supera su criterio de aceptación. Una maqueta, un botón sin acción o un endpoint vacío no cierran una tarea.

| Paso | Hito | Estado |
|---|---|---|
| 1 | Acceso, inventario y catálogo | Implementado; ver docs/VALIDACION.md |
| 2 | Reservas y disponibilidad por fechas | Pendiente |
| 3 | Pagos demo y facturas | Pendiente |
| 4 | Entregas, devoluciones y daños | Pendiente |
| 5 | Administración y reportes | Pendiente |
| 6 | Pasarela real en sandbox | Pendiente de selección y credenciales |
| 7 | Entrega reproducible | Pendiente |

Cada cierre debe registrar funciones terminadas, pruebas ejecutadas, evidencia de la demostración y pendientes. Ante un cambio de alcance, actualizar esta planeación y el desglose antes de cambiar reglas de negocio.
