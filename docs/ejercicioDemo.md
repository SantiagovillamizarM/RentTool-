> [!WARNING]
>
> 1. Se debe implementar arquitectura hexagonal + DDD y proyecto por módulos
> 2. Documentación:
>    - README PRINCIPAL
>    - DOCUMENTACION DE COMPILACION
>    - DOCUMENTACION DE DESPLIEGUE (DOCKER O TOMCAT NATIVO)
> 3. Para el frontend se puede usas (REACT, ANGULAR, VUE, JS VANILLA)
> 4. IMPLEMENTAR NORMALIZACION 4FN



# Plataforma de Renta de Herramientas y Equipos de Construcción

## Proyecto de Spring Boot



## **📌 Contexto del Proyecto**

El alquiler de herramientas y equipos de construcción es una necesidad frecuente para **contratistas, empresas y particulares** que requieren maquinaria específica sin necesidad de comprarla. Sin embargo, la gestión de reservas, disponibilidad y pagos suele ser un proceso manual o poco optimizado.

Este proyecto tiene como objetivo desarrollar una **Plataforma de Renta de Herramientas y Equipos de Construcción**, donde los **proveedores** puedan registrar su inventario y los **clientes** puedan **alquilar herramientas, programar entregas y gestionar pagos** de forma eficiente.



📌 **Funcionalidades clave del sistema:**

 ✅ Registro y gestión de herramientas con disponibilidad y costos.

 ✅ Sistema de reservas con pagos en línea y generación de facturas.

 ✅ Panel de control para proveedores y clientes con historial de alquileres.

 ✅ Gestión de devoluciones y estado del equipo alquilado.



Este sistema permitirá **automatizar el proceso de alquiler de herramientas**, mejorando la organización, el control de inventarios y la experiencia de los usuarios.



## **📌 Perfiles de Usuarios y Funcionalidades**

### **1️⃣ Administrador de la Plataforma (Rol: Admin)**

📌 **Acciones Disponibles:**

✅ **Gestión de usuarios:**

- Registrar y administrar proveedores y clientes.
- Verificar el estado de los equipos alquilados.

✅ **Control de alquileres y devoluciones:**

- Ver historial de alquileres.
- Administrar reportes de daños en herramientas.

✅ **Supervisión de pagos:**

- Monitorear pagos y facturación.

✅ **Reportes y métricas:**

- Estadísticas sobre ingresos, equipos más alquilados y rentabilidad.



### **2️⃣ Proveedor de Equipos (Rol: Proveedor)**

📌 **Acciones Disponibles:**

 ✅ **Gestión de inventario:**

- Agregar, editar y eliminar herramientas.
- Definir disponibilidad y costos de alquiler.

✅ **Gestión de reservas:**

- Aceptar o rechazar solicitudes de alquiler.
- Confirmar devoluciones y reportar daños.

✅ **Gestión de pagos y facturación:**

- Generar facturas por alquileres realizados.



### **3️⃣ Cliente (Rol: Cliente)**

📌 **Acciones Disponibles:**

 ✅ Registro e inicio de sesión con JWT.

 ✅ Explorar herramientas disponibles y consultar precios.

 ✅ Realizar reservas:

- Seleccionar fechas y confirmar alquiler.
- ✅ **Gestión de pagos:**
- Pagar en línea y descargar factura.
- ✅ **Historial de alquileres:**
- Revisar herramientas alquiladas anteriormente.



## **📌 Diseño y Requisitos del Frontend**

El frontend será la interfaz de usuario con un diseño **intuitivo, moderno y responsivo**.

📌 **Requisitos del Frontend:**

 ✅ Diseño adaptable para móviles, tabletas y escritorio.

 ✅ Interfaz basada en HTML, CSS y JavaScript puro.

 ✅ Panel de usuario personalizado según el rol (Administrador, Proveedor, Cliente).

 ✅ Experiencia de usuario optimizada, con navegación clara y funcionalidad intuitiva.



### **📌 Interacción del Usuario con el Sistema**

#### **1️⃣ Vista del Administrador**

📌 **Panel de control:**

 ✅ Lista de usuarios registrados (proveedores y clientes).

 ✅ Resumen de pagos y facturación global.

 ✅ Historial de alquileres y reportes de estado de equipos.



📌 **Módulo de supervisión:**

 ✅ Validación de devoluciones y estado de equipos.

 ✅ Gestión de incidencias con equipos dañados.



📌 **Módulo de reportes:**

 ✅ Generación de reportes de rentabilidad y uso de herramientas.



#### **2️⃣ Vista del Proveedor**

📌 **Módulo de herramientas:**

 ✅ Lista de herramientas en inventario con estado y disponibilidad.

 ✅ Carga de nuevas herramientas con imágenes y descripción.



📌 **Módulo de reservas:**

 ✅ Gestión de solicitudes de alquiler.

 ✅ Confirmación de devoluciones y reportes de daños.



📌 **Módulo de facturación:**

 ✅ Generación automática de facturas y comprobantes de pago.



#### **3️⃣ Vista del Cliente**

📌 **Módulo de exploración:**

 ✅ Búsqueda de herramientas por categoría y disponibilidad.

 ✅ Consulta de precios y detalles del alquiler.



📌 **Módulo de reservas:**

 ✅ Selección de fechas de alquiler y confirmación de pago.

 ✅ Historial de alquileres y estado de herramientas alquiladas.



📌 **Módulo de pagos:**

 ✅ Opciones de pago en línea y descarga de facturas.



## **📌 Tecnologías Utilizadas**

✅ **Backend:**

 🔹 Spring Boot (Última versión)

 🔹 Java |7 o superior

 🔹 Spring Security con JWT

 🔹 Spring Data JPA

 🔹 MySQL

✅ **Frontend:**

 🔹 HTML, CSS, JavaScript

 🔹 Bootstrap o Tailwind CSS (según lo autorice el trainer)

✅ **Documentación y Despliegue:**

 🔹 Swagger

 🔹 Apache Tomcat



## **📌 Entregables Obligatorios**

📌 **Cada equipo debe entregar:**

 ✅ Repositorio en GitHub con el código fuente.

 ✅ README.md bien estructurado.

 ✅ Scripts SQL para la creación y población de la base de datos.

 ✅ Diagrama relacional de la base de datos.

 ✅ Documentación Swagger de la API.

 ✅ Código modular y bien organizado.

Cada estudiante deberá completar el desarrollo de la **Plataforma de Renta de Herramientas y Equipos de Construcción** siguiendo los requisitos definidos en el enunciado. Para la evaluación y entrega del proyecto, se espera que los estudiantes cumplan con los siguientes entregables y características funcionales.

## **📌 1. Repositorio en GitHub con Código Fuente**

Cada equipo deberá proporcionar un **repositorio privado en GitHub**, compartido con el instructor, bajo el siguiente formato de nombre:

 📌 **Proyecto_RentaHerramientas_ApellidoNombre** (Ejemplo: Proyecto_RentaHerramientas_GomezCarlos).

El repositorio debe contener:

 ✅ Código modular y bien estructurado en **Spring Boot (última versión)**.

 ✅ Separación adecuada de capas (**Controllers, Services, Repositories, DTOs, Entities**).

 ✅ Código documentado con **comentarios explicativos** donde sea necesario.

 ✅ Commits frecuentes que evidencien el trabajo progresivo del equipo.



## **📌 2. Implementación Completa del Backend con Spring Boot**

El backend debe estar correctamente desarrollado con **Spring Boot y Java 21**, e incluir:

 ✅ **Autenticación y roles con JWT** (Administrador, Proveedor, Cliente).

 ✅ **CRUD completo** para clientes, proveedores, herramientas, reservas y pagos.

 ✅ **Gestión de seguridad con Spring Security y restricciones de acceso**.

 ✅ **Manejo de errores y excepciones personalizadas**.

 ✅ **Gestión de pagos y facturación automática**.

 ✅ **Sistema de control de disponibilidad de herramientas**.

 ✅ **Configuración de CORS** para permitir la comunicación con el frontend.



## **📌 3. Implementación de Base de Datos en MySQL**

La base de datos debe estar correctamente normalizada y configurada en **MySQL**, incluyendo:

 ✅ **Scripts SQL para la creación de la base de datos y las tablas**.

 ✅ **Scripts SQL con al menos 20 registros por cada entidad**.

 ✅ **Relaciones bien definidas entre las entidades** (Usuarios, Proveedores, Clientes, Herramientas, Reservas, Pagos).

 ✅ **Optimización en las consultas y uso de índices si es necesario**.



## **📌 4. Documentación Completa del Proyecto**

📌 **README.md bien estructurado** en el repositorio con:

 ✅ **Nombre y descripción del proyecto**.

 ✅ **Tecnologías utilizadas** (Java 17 mínimo, Spring Boot, MySQL, JWT).

 ✅ **Instrucciones para clonar, instalar dependencias y ejecutar el proyecto**.

 ✅ Ejemplo de configuración del application.properties.

 ✅ **Diagrama relacional de la base de datos en formato imagen**.

 ✅ **Lista de endpoints de la API documentados** con ejemplos de uso en JSON.

 ✅ **Explicación de la autenticación y roles**.

 ✅ **Guía para ejecutar pruebas unitarias y de integración**.



📌 **Swagger para documentación de la API** con:

 ✅ **Listado de endpoints accesibles** con detalles de peticiones y respuestas.

 ✅ **Ejemplos de solicitudes y respuestas JSON**.

 ✅ **Explicación de autenticación y uso de JWT en la API**.





## **📌 5. Implementación del Frontend con HTML, CSS y JavaScript**

El frontend debe proporcionar una experiencia de usuario **intuitiva, fluida y responsiva**, incluyendo:

 ✅ **Diseño adaptable** para móviles, tabletas y escritorio.

 ✅ **El trainer tiene la libertad de permitir frameworks de diseño como Bootstrap, Tailwind CSS, Vue.js o React.**

 ✅ **Autenticación e interacción con la API usando JWT**.

 ✅ **Panel de usuario personalizado según el rol:**

- Administrador: Puede gestionar herramientas, proveedores y clientes.
- Proveedor: Puede gestionar sus herramientas y reservas.
- Cliente: Puede realizar alquileres, ver su historial y pagar en línea.

✅ **Diseño atractivo y con experiencia de usuario optimizada.**



## **📌 6. Funcionalidades Esperadas en el Sistema**

📌 **Gestión de Proveedores y Clientes**

 ✅ Registro, edición y eliminación de proveedores y clientes.

 ✅ Gestión de permisos y acceso según el rol de usuario.



📌 **Gestión de Herramientas y Equipos**

 ✅ CRUD de herramientas con estado y disponibilidad.

 ✅ Carga de imágenes de herramientas y descripción detallada.



📌 **Gestión de Reservas y Alquileres**

 ✅ Sistema de reservas con selección de fechas y confirmación automática.

 ✅ Validación de disponibilidad antes de confirmar una reserva.

 ✅ Registro de entregas y devoluciones de herramientas.



📌 **Gestión de Pagos y Facturación**

 ✅ Registro y validación de pagos realizados por los clientes.

 ✅ Generación automática de facturas y comprobantes de pago.

 ✅ Historial de pagos y vencimiento de reservas.



📌 **Reportes y Estadísticas**

 ✅ Reporte de ingresos por alquileres.

 ✅ Estadísticas de herramientas más alquiladas y clientes frecuentes.

 ✅ Reporte de disponibilidad y estado de herramientas.



## **📌 7. Pruebas del Sistema**



📌 **Pruebas de API con Postman**:

 ✅ Peticiones **POST, GET, PUT y DELETE** correctamente implementadas.

 ✅ Pruebas de autenticación con JWT y protección de rutas.

 ✅ Simulación de flujos completos de alquiler y pagos.



📌 **Despliegue en Apache Tomcat**:

 ✅ Configuración del servidor embebido para ejecutar el backend.