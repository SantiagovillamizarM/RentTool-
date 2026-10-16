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
// Marca un campo con error: desplaza solo su contenedor desplazable hasta él y lo ilumina.
function fieldError(form, name, message) {
  const input = form.elements[name];
  const box = input?.closest("label") || input;
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
    [/logo|imagen/i, "logo"],
  ];
  return rules.find(([pattern]) => pattern.test(message))?.[1];
}
async function api(path, options = {}) {
  const headers = { ...options.headers };
  if (options.body && !(options.body instanceof FormData))
    headers["Content-Type"] = "application/json";
  if (state.token) headers.Authorization = `Bearer ${state.token}`;
  const response = await fetch("/api/v1" + path, { ...options, headers });
  if (!response.ok) {
    let error = {};
    try {
      error = await response.json();
    } catch {}
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
          }[response.status] ||
          "No se pudo completar la solicitud.",
      ),
      { status: response.status, code: error.code },
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
  const extra =
    r.status === "SOLICITADA" && r.respondBy
      ? `<p class="muted">Respuesta antes del ${dateTime(r.respondBy)}</p>`
      : r.note
        ? `<p class="muted">Motivo: ${esc(r.note)}</p>`
        : "";
  return `<article class="inventory-row reservation-row"><div><h3><span class="reservation-id">#${r.id}</span> ${esc(r.toolName)} <span class="status-chip status-${r.status.toLowerCase()}">${reservationStatus[r.status]}</span></h3><p class="muted">${shortDate(r.start)} → ${shortDate(r.end)} · ${r.days} ${r.days === 1 ? "día" : "días"} · ${r.quantity} ${r.quantity === 1 ? "unidad" : "unidades"} · ${money(r.total)}${who}</p>${extra}</div>${actions.length ? `<div class="inventory-actions">${actions.join("")}</div>` : ""}</article>`;
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
        (b.onclick = () =>
          act(
            b,
            () =>
              api(`/reservations/${b.dataset.accept}/accept`, {
                method: "POST",
              }),
            "Reserva aceptada. Las unidades quedaron asignadas.",
          )),
    );
    box.querySelectorAll("[data-reject]").forEach(
      (b) =>
        (b.onclick = () => {
          const reason = prompt("Motivo del rechazo (opcional):", "");
          if (reason === null) return;
          act(
            b,
            () =>
              api(`/reservations/${b.dataset.reject}/reject`, {
                method: "POST",
                body: JSON.stringify({ reason: reason.trim() || null }),
              }),
            "Solicitud rechazada.",
          );
        }),
    );
    box.querySelectorAll("[data-cancel]").forEach(
      (b) =>
        (b.onclick = () => {
          if (!confirm("¿Cancelar esta reserva? Las unidades quedarán libres."))
            return;
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
    if (!(field && fieldError(e.target, field, error.message)))
      $("#password-error").textContent = error.message;
  } finally {
    b.disabled = false;
  }
};
document
  .querySelectorAll("dialog .close")
  .forEach((b) => (b.onclick = () => b.closest("dialog").close()));
document.querySelectorAll("dialog").forEach((d) =>
  d.addEventListener("cancel", (e) => {
    e.preventDefault();
    d.close();
  }),
);
document.querySelectorAll("dialog").forEach((d) =>
  d.addEventListener("click", (e) => {
    if (e.target === d) {
      const r = d.getBoundingClientRect();
      if (
        e.clientX < r.left ||
        e.clientX > r.right ||
        e.clientY < r.top ||
        e.clientY > r.bottom
      )
        d.close();
    }
  }),
);
