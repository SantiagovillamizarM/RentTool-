"use strict";
// Código compartido por el sitio público (app.js) y el panel de administración (admin.js).
const $ = (s) => document.querySelector(s),
  esc = (s) =>
    String(s ?? "").replace(
      /[&<>"']/g,
      (c) =>
        ({
          "&": "&amp;",
          "<": "&lt;",
          ">": "&gt;",
          '"': "&quot;",
          "'": "&#39;",
        })[c],
    );
const state = {
  token: sessionStorage.getItem("renttool.token"),
  user: null,
  passwordRequired: false,
};
const DEFAULT_LOGO = "assets/icons/casco.png";
// Dígito de verificación DIAN: módulo 11 con pesos primos, igual que el backend.
function nitDigit(nit) {
  const weights = [3, 7, 13, 17, 19, 23, 29, 37, 41, 43, 47, 53, 59, 67, 71];
  let sum = 0;
  for (let i = 0; i < nit.length; i++)
    sum += Number(nit[nit.length - 1 - i]) * weights[i];
  const r = sum % 11;
  return r > 1 ? 11 - r : r;
}
const personLabel = { JURIDICA: "Persona jurídica", NATURAL: "Persona natural" };
/** NIT y representante legal: pista del dígito de verificación y campos según el tipo. */
function updateLegalFields(form) {
  const legalEntity = form.elements.personType.value === "JURIDICA";
  form.querySelector(".legal-rep").hidden = !legalEntity;
  form.elements.legalRepresentative.required =
    form.elements.representativeDocument.required = legalEntity;
  const nit = form.elements.nit.value.trim();
  const hint = form.querySelector(".nit-hint");
  if (!/^\d{5,15}$/.test(nit)) {
    hint.textContent = "Escribe el NIT sin puntos ni dígito de verificación.";
    hint.className = "muted nit-hint";
    return;
  }
  const expected = nitDigit(nit);
  const typed = form.elements.verificationDigit.value.trim();
  if (typed === "") {
    hint.textContent = `Según la DIAN, el dígito de verificación es ${expected}.`;
    hint.className = "muted nit-hint";
  } else if (Number(typed) !== expected) {
    hint.textContent = `El dígito no coincide: para este NIT debería ser ${expected}.`;
    hint.className = "muted nit-hint warn";
  } else {
    hint.textContent = `NIT ${nit}-${expected} válido.`;
    hint.className = "muted nit-hint ok";
  }
}
/** Las mismas reglas que el servidor para los datos legales, para señalar el campo antes de enviar. */
function legalProblem(form) {
  const v = (name) => form.elements[name].value.trim();
  if (!/^\d{5,15}$/.test(v("nit")))
    return ["nit", "El NIT debe tener entre 5 y 15 dígitos, sin puntos ni guion."];
  if (!/^\d$/.test(v("verificationDigit")))
    return ["verificationDigit", "Escribe el dígito de verificación."];
  if (Number(v("verificationDigit")) !== nitDigit(v("nit")))
    return ["verificationDigit", `No coincide con el NIT: debería ser ${nitDigit(v("nit"))}.`];
  if (!v("legalName")) return ["legalName", "Escribe la razón social o el nombre completo."];
  if (form.elements.personType.value === "JURIDICA") {
    if (!v("legalRepresentative"))
      return ["legalRepresentative", "Una persona jurídica requiere representante legal."];
    if (!v("representativeDocument"))
      return ["representativeDocument", "Escribe el documento del representante."];
  }
  if (!/^\+?[\d ]{7,20}$/.test(v("phone")))
    return ["phone", "El teléfono solo admite dígitos, espacios y +, mínimo 7."];
  if (!v("address")) return ["address", "Escribe la dirección."];
  if (!v("city")) return ["city", "Escribe la ciudad."];
  return null;
}
/** Datos legales del formulario listos para enviar. */
function legalPayload(form) {
  const f = (name) => form.elements[name].value.trim();
  const legalEntity = f("personType") === "JURIDICA";
  return {
    personType: f("personType"),
    nit: f("nit"),
    verificationDigit: Number(f("verificationDigit")),
    legalName: f("legalName"),
    tradeName: f("tradeName") || null,
    legalRepresentative: legalEntity ? f("legalRepresentative") : null,
    representativeDocument: legalEntity ? f("representativeDocument") : null,
    phone: f("phone"),
    address: f("address"),
    city: f("city"),
  };
}
/** Rol con que se emitió el token de esta pestaña (solo para decidir la interfaz). */
function tokenRole() {
  try {
    const payload = state.token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
    return JSON.parse(atob(payload)).role;
  } catch {
    return null;
  }
}
/** Un cliente aceptado como proveedor conserva un token viejo: se renueva con el rol nuevo. */
async function renewSession() {
  const session = await api("/auth/refresh", { method: "POST", renewing: true });
  saveSession(session);
  state.user = session.user;
  return session;
}
async function syncSessionRole() {
  if (state.user && tokenRole() !== state.user.role) await renewSession();
}
const SESSION_KEYS = ["renttool.token", "renttool.expires", "renttool.activity"];
/** Guarda el token recibido al iniciar sesión o al renovarlo. */
function saveSession(session) {
  state.token = session.token;
  sessionStorage.setItem("renttool.token", session.token);
  if (session.expiresAt) sessionStorage.setItem("renttool.expires", session.expiresAt);
}
function clearSession() {
  state.token = null;
  state.user = null;
  SESSION_KEYS.forEach((k) => sessionStorage.removeItem(k));
}
/*
 * Sesión del administrador: se cierra sola tras 10 minutos sin actividad. El servidor emite tokens
 * de 10 minutos y aquí se renuevan mientras hay actividad; sin ella, el token caduca también allá.
 * La última actividad se guarda en sessionStorage para que cuente igual en /admin y en el sitio.
 */
const ADMIN_IDLE_MS = 10 * 60 * 1000;
const REFRESH_WHEN_LEFT_MS = 7 * 60 * 1000;
const ACTIVITY_EVENTS = ["pointerdown", "pointermove", "keydown", "wheel", "scroll", "touchstart"];
const adminIdle = { watching: false, timer: null, lastMark: 0, refreshing: false };
const lastActivity = () => Number(sessionStorage.getItem("renttool.activity")) || 0;
function markActivity() {
  const now = Date.now();
  if (now - adminIdle.lastMark < 1000) return;
  adminIdle.lastMark = now;
  sessionStorage.setItem("renttool.activity", String(now));
  refreshAdminToken();
}
async function refreshAdminToken() {
  const expires = Date.parse(sessionStorage.getItem("renttool.expires") || "");
  if (adminIdle.refreshing || (expires && expires - Date.now() > REFRESH_WHEN_LEFT_MS)) return;
  adminIdle.refreshing = true;
  try {
    saveSession(await api("/auth/refresh", { method: "POST" }));
  } catch {
    // Si la sesión ya no es válida, api() la cierra.
  } finally {
    adminIdle.refreshing = false;
  }
}
function checkAdminIdle() {
  if (adminIdle.watching && Date.now() - lastActivity() >= ADMIN_IDLE_MS) endAdminSession();
}
function watchAdminIdle() {
  if (adminIdle.watching) return;
  adminIdle.watching = true;
  // Al volver tras suspender el equipo o recargar, la inactividad acumulada sigue contando.
  if (!lastActivity()) sessionStorage.setItem("renttool.activity", String(Date.now()));
  checkAdminIdle();
  if (!adminIdle.watching) return;
  ACTIVITY_EVENTS.forEach((e) =>
    addEventListener(e, markActivity, { capture: true, passive: true }),
  );
  document.addEventListener("visibilitychange", checkAdminIdle);
  adminIdle.timer = setInterval(checkAdminIdle, 5000);
}
/** Cierra la sesión sin preguntar y vuelve a pedir el inicio de sesión, con la página limpia. */
function endAdminSession(
  notice = "Por seguridad, tu sesión de administrador se cerró tras 10 minutos sin actividad. Inicia sesión de nuevo.",
) {
  adminIdle.watching = false;
  clearInterval(adminIdle.timer);
  ACTIVITY_EVENTS.forEach((e) =>
    removeEventListener(e, markActivity, { capture: true }),
  );
  clearSession();
  sessionStorage.setItem("renttool.notice", notice);
  if (location.pathname === "/") {
    history.replaceState(null, "", "/#acceso");
    location.reload();
  } else location.replace("/#acceso");
}
const money = (v) =>
  new Intl.NumberFormat("es-CO", {
    style: "currency",
    currency: "COP",
    maximumFractionDigits: 0,
  }).format(v);
// Imagen de una herramienta: su imagen principal o, si no tiene, la ilustración predeterminada.
const toolArt = (id = 0) =>
  `<svg viewBox="0 0 300 200" aria-hidden="true"><defs><linearGradient id="metal${id}" x2="0.7" y2="1"><stop stop-color="#929296"/><stop offset=".4" stop-color="#3e3e43"/><stop offset="1" stop-color="#19191c"/></linearGradient><linearGradient id="gold${id}" x2="0" y2="1"><stop stop-color="#ffdf21"/><stop offset="1" stop-color="#b89a00"/></linearGradient></defs><ellipse cx="147" cy="175" rx="92" ry="9" fill="#000" opacity=".35"/><g transform="rotate(-12 150 100)"><path d="M70 70h103l20 20-5 41H80l-16-15z" fill="url(#gold${id})"/><path d="M82 125l23 3-9 47H64l6-16z" fill="#202024"/><path d="M67 166h39l5 17H60z" fill="url(#gold${id})"/><path d="M156 73h44v52h-44z" fill="url(#metal${id})"/><path d="M200 84h20v30h-20z" fill="#b3b3b7"/><path d="M219 94h49v10h-49z" fill="url(#metal${id})"/><path d="M107 83h36v7h-36z M107 98h36v7h-36z" fill="#2d2b1b"/><path d="M91 127h13v14H91z" fill="#9c9ca2"/><path d="M72 75h25v41H70z" fill="#e8c710"/><circle cx="170" cy="100" r="8" fill="#333339"/></g></svg>`;
function imageMarkup(t) {
  const url = t.images?.[0]?.location;
  if (url && /^(https:\/\/|\/uploads\/|\/assets\/)/.test(url))
    return `<img src="${esc(url)}" alt="${esc(t.name)}" loading="lazy" referrerpolicy="no-referrer">`;
  return toolArt(t.id);
}
function wireImages(parent) {
  parent.querySelectorAll("img").forEach((img) =>
    img.addEventListener(
      "error",
      () => {
        img.outerHTML = toolArt("fallback");
      },
      { once: true },
    ),
  );
}
/** Muestra un aviso; con {label, href} añade un enlace, por ejemplo "Ver cambios". */
function notify(message, action) {
  const toast = $("#toast");
  toast.textContent = message;
  if (action) {
    const link = document.createElement("a");
    link.className = "toast-action";
    link.href = action.href;
    link.textContent = action.label;
    toast.append(link);
  }
  toast.classList.remove("leaving");
  toast.hidden = false;
  clearTimeout(notify.timer);
  notify.timer = setTimeout(
    () => {
      toast.classList.add("leaving");
      notify.timer = setTimeout(() => (toast.hidden = true), 250);
    },
    action ? 8000 : 4500,
  );
}
// Los diálogos se cierran con un desvanecido: close() espera a que termine la animación.
const reducedMotion = matchMedia("(prefers-reduced-motion: reduce)");
const nativeClose = HTMLDialogElement.prototype.close;
const nativeShowModal = HTMLDialogElement.prototype.showModal;
HTMLDialogElement.prototype.close = function (value) {
  if (!this.open || this.classList.contains("closing")) return;
  if (reducedMotion.matches) return nativeClose.call(this, value);
  this.classList.add("closing");
  this.closeTimer = setTimeout(() => {
    this.classList.remove("closing");
    nativeClose.call(this, value);
  }, 200);
};
HTMLDialogElement.prototype.showModal = function () {
  if (this.classList.contains("closing")) {
    clearTimeout(this.closeTimer);
    this.classList.remove("closing");
    return;
  }
  nativeShowModal.call(this);
};
// Una sola confirmación a la vez. Cerrar o pulsar Escape nunca ejecuta la acción.
let confirmationOpen = false;
function confirmAction({ title, message, confirmLabel, danger = false, reason = false }) {
  if (confirmationOpen) return Promise.resolve(null);
  confirmationOpen = true;
  const previousFocus = document.activeElement;
  const dialog = document.createElement("dialog");
  dialog.className = "action-confirmation";
  dialog.setAttribute("aria-labelledby", "confirmation-title");
  dialog.setAttribute("aria-describedby", "confirmation-message");
  dialog.innerHTML = `<h2 id="confirmation-title">${esc(title)}</h2>
    <p id="confirmation-message" class="muted"></p>
    <form>
      ${reason ? '<label>Motivo del rechazo (opcional)<textarea name="reason" maxlength="500" rows="3"></textarea></label>' : ""}
      <div class="confirmation-actions">
        <button type="button" class="outline-button" data-cancel autofocus>Volver sin cambios</button>
        <button type="submit" class="button ${danger ? "danger-button" : ""}">${esc(confirmLabel)}</button>
      </div>
    </form>`;
  dialog.querySelector("#confirmation-message").textContent = message;
  document.body.append(dialog);
  return new Promise((resolve) => {
    let result = null;
    let submitted = false;
    dialog.querySelector("[data-cancel]").onclick = () => dialog.close();
    dialog.querySelector("form").onsubmit = (event) => {
      event.preventDefault();
      if (submitted || dialog.classList.contains("closing")) return;
      submitted = true;
      result = { reason: reason ? event.target.elements.reason.value.trim() || null : null };
      dialog.querySelector('button[type="submit"]').disabled = true;
      dialog.close();
    };
    dialog.addEventListener("close", () => {
      dialog.remove();
      confirmationOpen = false;
      if (previousFocus?.isConnected) previousFocus.focus({ preventScroll: true });
      resolve(result);
    }, { once: true });
    dialog.showModal();
  });
}
// Marca un campo con error: desplaza solo su contenedor desplazable hasta él y lo ilumina.
function fieldError(form, name, message) {
  const input = form.elements[name];
  let box = input?.closest("label");
  if (input && !box) {
    box = input.closest(".field-wrapper");
    if (!box) {
      box = document.createElement("div");
      box.className = "field-wrapper";
      input.before(box);
      box.append(input);
    }
  }
  if (!box) return false;
  box.classList.add("has-error");
  let note = box.querySelector(".field-error");
  if (!note) {
    note = document.createElement("small");
    note.className = "field-error";
    note.setAttribute("role", "alert");
    box.append(note);
  }
  note.textContent = message;
  reveal(box, form.closest("dialog"));
  input.focus?.({ preventScroll: true });
  return true;
}
// Centra el elemento desplazando solo su diálogo (o la página si no está en uno) y lo ilumina.
function reveal(element, scroller = document.scrollingElement) {
  element.classList.remove("field-glow");
  void element.offsetWidth;
  element.classList.add("field-glow");
  clearTimeout(element.glowTimer);
  element.glowTimer = setTimeout(() => element.classList.remove("field-glow"), 1500);
  const offset =
    scroller === document.scrollingElement ? 0 : scroller.getBoundingClientRect().top;
  const top =
    element.getBoundingClientRect().top -
    offset +
    scroller.scrollTop -
    scroller.clientHeight / 2 +
    element.offsetHeight / 2;
  scroller.scrollTo({
    top: Math.max(0, top),
    behavior: reducedMotion.matches ? "auto" : "smooth",
  });
}
function clearFieldErrors(form) {
  form
    .querySelectorAll(".has-error, .field-glow")
    .forEach((b) => b.classList.remove("has-error", "field-glow"));
  form.querySelectorAll(".field-error").forEach((n) => n.remove());
}
document.addEventListener("input", (e) => {
  const box = e.target.closest?.(".has-error");
  if (!box) return;
  box.classList.remove("has-error");
  box.querySelector(".field-error")?.remove();
});
// Relaciona el mensaje del servidor con el campo que lo causó.
function errorField(message) {
  const field = /(?:^|; )(?:profile\.)?(\w+):/.exec(message);
  if (field) return field[1];
  const rules = [
    [/dígito de verificación/i, "verificationDigit"],
    [/NIT/, "nit"],
    [/correo/i, "email"],
    [/contraseña/i, "temporaryPassword"],
    [/representante/i, "legalRepresentative"],
    [/razón social|nombre completo/i, "legalName"],
    [/teléfono/i, "phone"],
    [/dirección/i, "address"],
    [/ciudad/i, "city"],
    [/categoría/i, "categoryId"],
    [/tarifa/i, "dailyRate"],
    [/descripción/i, "description"],
    [/código/i, "code"],
    [/cantidad/i, "quantity"],
    [/devolución/i, "end"],
    [/fechas pasadas|fecha de inicio/i, "start"],
    [/nombre/i, "name"],
    [/logo|imagen/i, "logo"],
  ];
  return rules.find(([pattern]) => pattern.test(message))?.[1];
}
// Los errores de validación llegan por campo, sin depender del texto del mensaje.
function showFormError(form, error, summary, fallbackField) {
  clearFieldErrors(form);
  const unmatched = [];
  const entries = Object.entries(error.fieldErrors || {});
  // Orden inverso para dejar el foco en el primer campo inválido.
  let marked = false;
  for (const [path, message] of entries.reverse()) {
    const name = path.split(".").pop();
    marked = fieldError(form, name, message) || marked;
    if (!form.elements[name]) unmatched.unshift(message);
  }
  if (!entries.length) {
    const name = fallbackField || errorField(error.message);
    if (name) marked = fieldError(form, name, error.message);
  }
  if (summary) {
    summary.textContent = unmatched.length ? unmatched.join("; ") : marked ? "Corrige los campos señalados." : error.message;
    if (!marked) reveal(summary, form.closest("dialog") || document.scrollingElement);
  }
  return marked;
}
async function api(path, options = {}) {
  const headers = { ...options.headers };
  if (options.body && !(options.body instanceof FormData))
    headers["Content-Type"] = "application/json";
  if (state.token) headers.Authorization = `Bearer ${state.token}`;
  let response;
  try {
    response = await fetch("/api/v1" + path, { ...options, headers });
  } catch {
    throw new Error("No se pudo conectar con RentTool. Revisa tu conexión e inténtalo de nuevo.");
  }
  if (!response.ok) {
    let error = {};
    try {
      error = await response.json();
    } catch {}
    // El rol cambió (cliente aceptado como proveedor): se renueva el token y se repite una vez.
    if (error.code === "ROLE_CHANGED" && !options.renewing && !options.retried) {
      await renewSession();
      return api(path, { ...options, retried: true });
    }
    if (response.status === 401 && state.token) {
      if (state.user?.role === "ADMINISTRADOR")
        endAdminSession("Tu sesión de administrador venció. Inicia sesión de nuevo.");
      else {
        logout();
        notify("Tu sesión ha vencido. Inicia sesión nuevamente.");
      }
    }
    if (error.code === "PASSWORD_CHANGE_REQUIRED") openPassword(true);
    throw Object.assign(
      new Error(
        error.detail ||
          {
            401: "Inicia sesión para continuar.",
            403: "No tienes permiso para esta acción.",
            400: "Revisa los datos introducidos e inténtalo de nuevo.",
            404: "El recurso ya no está disponible. Actualiza la lista.",
            409: "La operación entra en conflicto con los datos actuales. Actualiza e inténtalo de nuevo.",
            413: "El archivo supera el tamaño permitido. Selecciona una imagen de hasta 5 MB.",
            429: "Hay demasiadas solicitudes. Espera un momento antes de intentarlo de nuevo.",
          }[response.status] ||
          (response.status >= 500
            ? "RentTool no pudo completar la operación. Inténtalo de nuevo en unos momentos."
            : "No se pudo completar la solicitud. Inténtalo de nuevo."),
      ),
      { status: response.status, code: error.code, fieldErrors: error.fieldErrors },
    );
  }
  return response.status === 204 ? null : response.json();
}
// Las fechas de alquiler son días de la zona del negocio, igual que en el backend.
const businessToday = () =>
  new Intl.DateTimeFormat("en-CA", { timeZone: "America/Bogota" }).format(
    new Date(),
  );
function addDays(isoDate, days) {
  const [y, m, d] = isoDate.split("-").map(Number);
  const date = new Date(Date.UTC(y, m - 1, d + days));
  return date.toISOString().slice(0, 10);
}
const shortDate = (isoDate) => {
  const [y, m, d] = isoDate.split("-").map(Number);
  return new Intl.DateTimeFormat("es-CO", {
    day: "numeric",
    month: "short",
  }).format(new Date(y, m - 1, d));
};
const dateTime = (instant) =>
  new Intl.DateTimeFormat("es-CO", {
    day: "numeric",
    month: "short",
    hour: "numeric",
    minute: "2-digit",
    timeZone: "America/Bogota",
  }).format(new Date(instant));
const reservationStatus = {
  SOLICITADA: "Pendiente",
  ACEPTADA: "Aceptada",
  RECHAZADA: "Rechazada",
  CANCELADA: "Cancelada",
  VENCIDA: "Vencida",
};
function reservationRow(r) {
  const role = state.user.role;
  const actions = [];
  if (role === "PROVEEDOR" && r.status === "SOLICITADA")
    actions.push(
      `<button class="button compact" data-accept="${r.id}">Aceptar</button>`,
      `<button class="outline-button" data-reject="${r.id}">Rechazar</button>`,
    );
  const cancellable =
    r.status === "SOLICITADA" ||
    (r.status === "ACEPTADA" && r.start > businessToday());
  if (role !== "PROVEEDOR" && cancellable)
    actions.push(
      `<button class="outline-button" data-cancel="${r.id}">Cancelar</button>`,
    );
  actions.push(
    `<button class="outline-button" data-history="${r.id}">Historial</button>`,
  );
  const provider = state.providers?.[r.providerId]?.name;
  const who =
    (role === "CLIENTE" ? "" : ` · Cliente: ${esc(r.clientName)}`) +
    (provider ? ` · Proveedor: ${esc(provider)}` : "");
  // El proveedor llama al cliente para acordar el pago y la entrega.
  const contact =
    role !== "CLIENTE" && (r.clientPhone || r.clientEmail)
      ? `<p class="client-contact">${r.clientPhone ? `<a href="tel:${esc(r.clientPhone.replace(/ /g, ""))}">☎ ${esc(r.clientPhone)}</a>` : ""}${r.clientEmail ? `<a href="mailto:${esc(r.clientEmail)}">✉ ${esc(r.clientEmail)}</a>` : ""}</p>`
      : "";
  const extra =
    r.status === "SOLICITADA" && r.respondBy
      ? `<p class="muted">Respuesta antes del ${dateTime(r.respondBy)}</p>`
      : r.note
        ? `<p class="muted">Motivo: ${esc(r.note)}</p>`
        : "";
  return `<article class="inventory-row reservation-row"><div><h3><span class="reservation-id">#${r.id}</span> ${esc(r.toolName)} <span class="status-chip status-${r.status.toLowerCase()}">${reservationStatus[r.status]}</span></h3><p class="muted">${shortDate(r.start)} → ${shortDate(r.end)} · ${r.days} ${r.days === 1 ? "día" : "días"} · ${r.quantity} ${r.quantity === 1 ? "unidad" : "unidades"} · ${money(r.total)}${who}</p>${contact}${extra}</div>${actions.length ? `<div class="inventory-actions">${actions.join("")}</div>` : ""}</article>`;
}
async function loadReservations() {
  const box = $("#reservations");
  if (!state.user) return;
  try {
    const data = await api("/reservations?size=50");
    const empty = {
      CLIENTE:
        "Aún no tienes reservas. Abre una herramienta del catálogo y elige tus fechas.",
      PROVEEDOR: "Cuando un cliente reserve tus herramientas, lo verás aquí.",
      ADMINISTRADOR: "Todavía no hay reservas en la plataforma.",
    }[state.user.role];
    box.innerHTML = data.content.length
      ? data.content.map(reservationRow).join("")
      : `<p class="muted">${empty}</p>`;
    // Avisa a la página (por ejemplo, para actualizar contadores) cada vez que la lista cambia.
    document.dispatchEvent(new CustomEvent("reservations:loaded", { detail: data }));
    const act = async (button, request, message) => {
      button.disabled = true;
      try {
        await request();
        notify(message);
      } catch (e) {
        notify(e.message);
      }
      loadReservations();
    };
    box
      .querySelectorAll("[data-history]")
      .forEach((b) => (b.onclick = () => openHistory(b.dataset.history)));
    box.querySelectorAll("[data-accept]").forEach(
      (b) =>
        (b.onclick = async () => {
          const r = data.content.find((r) => String(r.id) === b.dataset.accept);
          if (!(await confirmAction({
            title: `¿Aceptar la reserva #${r.id}?`,
            message: `${r.toolName} · ${r.quantity} unidades · ${shortDate(r.start)} → ${shortDate(r.end)}.\nSe asignarán unidades concretas y quedarán ocupadas durante esas fechas. Total: ${money(r.total)}.`,
            confirmLabel: "Aceptar reserva",
          }))) return;
          act(
            b,
            () =>
              api(`/reservations/${b.dataset.accept}/accept`, {
                method: "POST",
              }),
            "Reserva aceptada. Las unidades quedaron asignadas.",
          );
        }),
    );
    box.querySelectorAll("[data-reject]").forEach(
      (b) =>
        (b.onclick = async () => {
          const r = data.content.find((r) => String(r.id) === b.dataset.reject);
          const confirmation = await confirmAction({
            title: `¿Rechazar la reserva #${r.id}?`,
            message: `${r.toolName} · ${shortDate(r.start)} → ${shortDate(r.end)}.\nLa solicitud quedará rechazada. El cliente podrá consultar el motivo. Esta solicitud todavía no ocupa unidades.`,
            confirmLabel: "Rechazar solicitud", danger: true, reason: true,
          });
          if (!confirmation) return;
          act(
            b,
            () =>
              api(`/reservations/${b.dataset.reject}/reject`, {
                method: "POST",
                body: JSON.stringify({ reason: confirmation.reason }),
              }),
            "Solicitud rechazada.",
          );
        }),
    );
    box.querySelectorAll("[data-cancel]").forEach(
      (b) =>
        (b.onclick = async () => {
          const r = data.content.find((r) => String(r.id) === b.dataset.cancel);
          if (!(await confirmAction({
            title: `¿Cancelar la reserva #${r.id}?`,
            message: `${r.toolName} · ${r.quantity} unidades · ${shortDate(r.start)} → ${shortDate(r.end)}.\n${r.status === "ACEPTADA" ? "Las unidades asignadas quedarán libres para otras reservas." : "La solicitud dejará de estar pendiente; todavía no tiene unidades asignadas."} Para reservar de nuevo tendrás que enviar otra solicitud.`,
            confirmLabel: "Cancelar reserva", danger: true,
          }))) return;
          act(
            b,
            () =>
              api(`/reservations/${b.dataset.cancel}`, { method: "DELETE" }),
            "Reserva cancelada.",
          );
        }),
    );
  } catch (e) {
    box.innerHTML = `<p class="muted">${esc(e.message)}</p>`;
  }
}
// Historial de una reserva: quién hizo cada cambio y cuándo.
const actorRoleLabel = {
  CLIENTE: "Cliente",
  PROVEEDOR: "Proveedor",
  ADMINISTRADOR: "Administrador",
  SISTEMA: "Automático",
};
document.body.insertAdjacentHTML(
  "beforeend",
  `<dialog id="history-dialog">
      <button class="close" aria-label="Cerrar">×</button>
      <p class="eyebrow">HISTORIAL DE LA RESERVA</p>
      <h2 id="history-title">Reserva</h2>
      <ol class="timeline" id="history-list"></ol>
    </dialog>`,
);
async function openHistory(id) {
  $("#history-title").textContent = `Reserva #${id}`;
  $("#history-list").innerHTML = '<li class="muted">Consultando historial…</li>';
  if (!$("#history-dialog").open) $("#history-dialog").showModal();
  try {
    const entries = await api(`/reservations/${id}/history`);
    $("#history-list").innerHTML = entries
      .map((h) =>
        h.recorded
          ? `<li class="timeline-item is-${h.action.toLowerCase()}"><span class="timeline-dot"></span><div><p class="timeline-head"><strong>${reservationStatus[h.action]}</strong><time datetime="${esc(h.at)}">${fullDateTime(h.at)}</time></p><p class="muted">${esc(h.actorName)} · ${actorRoleLabel[h.actorRole]}</p>${h.detail ? `<p class="muted timeline-detail">${esc(h.detail)}</p>` : ""}</div></li>`
          : `<li class="timeline-item unrecorded"><span class="timeline-dot"></span><div><p class="timeline-head"><strong>${reservationStatus[h.action]}</strong><span class="muted">Sin registro</span></p><p class="muted">Este cambio ocurrió antes de que existiera el historial: no se sabe cuándo ni quién lo hizo.</p></div></li>`,
      )
      .join("");
  } catch (e) {
    $("#history-list").innerHTML = `<li class="muted">${esc(e.message)}</li>`;
  }
}
const fullDateTime = (instant) =>
  new Intl.DateTimeFormat("es-CO", {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone: "America/Bogota",
  }).format(new Date(instant));
// El diálogo de contraseña existe en ambas páginas; se inserta una sola vez desde aquí.
document.body.insertAdjacentHTML(
  "beforeend",
  `    <dialog id="password-dialog">
      <button class="close" aria-label="Cerrar">×</button>
      <p class="eyebrow">SEGURIDAD DE TU CUENTA</p>
      <h2>Elige tu contraseña.</h2>
      <p class="muted" id="password-description"></p>
      <form id="password-form">
        <label
          >Contraseña actual<input
            name="currentPassword"
            type="password"
            required
            autocomplete="current-password"
            maxlength="64" /></label
        ><label
          >Nueva contraseña<input
            name="newPassword"
            type="password"
            required
            minlength="10"
            maxlength="64"
            autocomplete="new-password" /></label
        ><label
          >Repite la nueva contraseña<input
            name="confirm"
            type="password"
            required
            minlength="10"
            maxlength="64"
            autocomplete="new-password"
        /></label>
        <p class="form-error" id="password-error" role="alert"></p>
        <button class="button full">Guardar contraseña →</button>
      </form>
    </dialog>`,
);
function openPassword(required) {
  state.passwordRequired = required;
  $("#password-form").reset();
  clearFieldErrors($("#password-form"));
  $("#password-error").textContent = "";
  $("#password-description").textContent = required
    ? "Tu cuenta tiene una contraseña temporal. Cámbiala para empezar a usar RentTool. Si cierras esta ventana, se cerrará tu sesión."
    : "Escribe tu contraseña actual y la nueva, de 10 a 64 caracteres.";
  if (!$("#password-dialog").open) $("#password-dialog").showModal();
}
$("#change-password").onclick = () => openPassword(false);
$("#password-dialog").addEventListener("close", () => {
  if (state.passwordRequired && state.user?.mustChangePassword) {
    logout();
    notify("Sesión cerrada. Cambia tu contraseña temporal la próxima vez.");
  }
});
$("#password-form").onsubmit = async (e) => {
  e.preventDefault();
  const data = Object.fromEntries(new FormData(e.target));
  clearFieldErrors(e.target);
  $("#password-error").textContent = "";
  if (data.newPassword.length < 10) {
    fieldError(e.target, "newPassword", "Usa entre 10 y 64 caracteres.");
    return;
  }
  if (data.newPassword !== data.confirm) {
    fieldError(e.target, "confirm", "Las contraseñas nuevas no coinciden.");
    return;
  }
  const b = e.target.querySelector("button");
  b.disabled = true;
  try {
    state.user = await api("/me/password", {
      method: "PUT",
      body: JSON.stringify({
        currentPassword: data.currentPassword,
        newPassword: data.newPassword,
      }),
    });
    state.passwordRequired = false;
    $("#password-dialog").close();
    renderAccount();
    notify("Contraseña actualizada.");
  } catch (error) {
    const field = /actual/i.test(error.message)
      ? "currentPassword"
      : /nueva|caracteres/i.test(error.message)
        ? "newPassword"
        : null;
    showFormError(e.target, error, $("#password-error"), field);
  } finally {
    b.disabled = false;
  }
};
/** Pestañas de un panel ([data-tab] y [data-panel]) sincronizadas con el #hash de la URL. */
function setupTabs(defaultTab) {
  const show = () => {
    const tab = location.hash.slice(1) || defaultTab;
    const known = [...document.querySelectorAll("[data-panel]")].some(
      (p) => p.dataset.panel === tab,
    );
    const current = known ? tab : defaultTab;
    document
      .querySelectorAll("[data-panel]")
      .forEach((p) => (p.hidden = p.dataset.panel !== current));
    document.querySelectorAll("[data-tab]").forEach((a) => {
      const active = a.dataset.tab === current;
      a.classList.toggle("active", active);
      if (active) a.setAttribute("aria-current", "page");
      else a.removeAttribute("aria-current");
    });
  };
  addEventListener("hashchange", show);
  return show;
}
// Cierre de diálogos delegado en el documento: sirve también para los que se insertan después.
document.addEventListener("click", (e) => {
  const closeButton = e.target.closest?.("dialog .close");
  if (closeButton) {
    closeButton.closest("dialog").close();
    return;
  }
  // Clic en el fondo: el evento llega al propio diálogo, fuera de su caja.
  const d = e.target;
  if (d instanceof HTMLDialogElement && d.open) {
    const r = d.getBoundingClientRect();
    if (e.clientX < r.left || e.clientX > r.right || e.clientY < r.top || e.clientY > r.bottom)
      d.close();
  }
});
// "cancel" (Escape) no burbujea: se escucha en captura para cerrarlo con la animación.
document.addEventListener(
  "cancel",
  (e) => {
    if (!(e.target instanceof HTMLDialogElement)) return;
    e.preventDefault();
    e.target.close();
  },
  true,
);
