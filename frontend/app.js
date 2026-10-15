"use strict";
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
  categories: [],
  page: 0,
  totalPages: 0,
  mode: "login",
  editing: null,
  managing: null,
};
const money = (v) =>
  new Intl.NumberFormat("es-CO", {
    style: "currency",
    currency: "COP",
    maximumFractionDigits: 0,
  }).format(v);
function notify(message) {
  $("#toast").textContent = message;
  $("#toast").hidden = false;
  clearTimeout(notify.timer);
  notify.timer = setTimeout(() => ($("#toast").hidden = true), 4500);
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
      logout();
      notify("Tu sesión ha vencido. Inicia sesión nuevamente.");
    }
    throw new Error(
      error.detail ||
        {
          401: "Inicia sesión para continuar.",
          403: "No tienes permiso para esta acción.",
        }[response.status] ||
        "No se pudo completar la solicitud.",
    );
  }
  return response.status === 204 ? null : response.json();
}
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
async function loadCategories() {
  try {
    state.categories = await api("/categories");
    $("#category-grid").innerHTML = state.categories
      .map(
        (c, i) =>
          `<button class="category-card" data-category="${c.id}"><span class="category-icon">${esc(c.icon)}</span><h3>${esc(c.name)}</h3><p>${["Potencia para cada tarea.", "Equipos para transformar.", "Cada detalle cuenta.", "Llega un poco más lejos."][i % 4]}</p><span class="category-arrow">→</span></button>`,
      )
      .join("");
    const options = state.categories
      .map((c) => `<option value="${c.id}">${esc(c.name)}</option>`)
      .join("");
    $("#category-filter").innerHTML =
      '<option value="">Todas las categorías</option>' + options;
    $("#tool-category").innerHTML = options;
    $("#category-grid")
      .querySelectorAll("button")
      .forEach(
        (b) =>
          (b.onclick = () => {
            $("#category-filter").value = b.dataset.category;
            state.page = 0;
            loadTools();
            location.hash = "catalogo";
          }),
      );
  } catch (error) {
    $("#category-grid").innerHTML =
      `<p class="muted">${esc(error.message)} Comprueba que el backend esté iniciado.</p>`;
  }
}
async function loadTools() {
  const container = $("#products");
  container.innerHTML = '<div class="empty">Consultando herramientas…</div>';
  try {
    const params = new URLSearchParams({
      q: $("#search").value,
      page: state.page,
      size: 6,
    });
    if ($("#category-filter").value)
      params.set("categoryId", $("#category-filter").value);
    const data = await api("/tools?" + params);
    state.totalPages = data.totalPages;
    $("#result-count").textContent =
      `${data.totalElements} herramientas en catálogo`;
    container.innerHTML = data.content.length
      ? data.content
          .map(
            (t) =>
              `<article class="product"><div class="product-image"><span class="product-tag">${t.operationalUnits > 0 ? "EQUIPO OPERATIVO" : "CONSULTAR INVENTARIO"}</span>${imageMarkup(t)}</div><div class="product-body"><p class="product-category">${esc(t.category)}</p><h3>${esc(t.name)}</h3><p class="stock">${t.operationalUnits} unidades operativas</p><div class="product-bottom"><p class="price">${money(t.dailyRate)} <small>COP / día</small></p><button class="icon-button" data-detail="${t.id}" aria-label="Ver ${esc(t.name)}">↗</button></div></div></article>`,
          )
          .join("")
      : '<div class="empty"><h3>Tu próxima herramienta está por llegar.</h3><p>No hay publicaciones para estos filtros.<br>Regístrate como proveedor para añadir equipo al catálogo.</p><button class="outline-button" data-empty-register>Publicar mi inventario ↗</button></div>';
    container
      .querySelectorAll("[data-detail]")
      .forEach((b) => (b.onclick = () => detail(b.dataset.detail)));
    container
      .querySelector("[data-empty-register]")
      ?.addEventListener("click", () => openAuth("register"));
    wireImages(container);
    $("#page-label").textContent =
      `${data.totalPages ? state.page + 1 : 0} / ${data.totalPages}`;
    $("#prev").disabled = state.page === 0;
    $("#next").disabled = state.page + 1 >= data.totalPages;
  } catch (error) {
    container.innerHTML = `<div class="empty"><h3>No pudimos consultar el catálogo.</h3><p>${esc(error.message)}</p><button class="outline-button" id="retry-catalog">Reintentar</button></div>`;
    $("#result-count").textContent = "Conexión no disponible";
    $("#retry-catalog").onclick = loadTools;
    $("#prev").disabled = $("#next").disabled = true;
  }
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
async function detail(id) {
  try {
    const t = await api("/tools/" + id);
    const start = addDays(businessToday(), 1);
    $("#detail-content").innerHTML =
      `<div class="detail-image">${imageMarkup(t)}</div><p class="eyebrow">${esc(t.category)}</p><h2>${esc(t.name)}</h2><p class="muted">${esc(t.description)}</p><p class="detail-price">${money(t.dailyRate)} <small>COP / día</small></p><p class="stock">${t.operationalUnits} unidades operativas</p>` +
      `<form id="reserve-form" class="reserve-form" novalidate><h3>Reserva por fechas</h3><div class="reserve-fields"><label>Entrega<input type="date" name="start" required min="${businessToday()}" value="${start}"></label><label>Devolución<input type="date" name="end" required min="${addDays(start, 1)}" value="${addDays(start, 2)}"></label><label>Unidades<input type="number" name="quantity" required min="1" max="20" value="1"></label></div><p class="reserve-quote" aria-live="polite"></p><p class="form-error" role="alert"></p><button class="button full">Solicitar reserva →</button><p class="muted reserve-hint">El día de devolución no se cobra. El proveedor tiene 24 horas para aceptar.</p></form>`;
    wireImages($("#detail-content"));
    wireReservation(t);
    $("#detail-dialog").showModal();
  } catch (e) {
    notify(e.message);
  }
}
function wireReservation(tool) {
  const form = $("#reserve-form");
  const quote = form.querySelector(".reserve-quote");
  const error = form.querySelector(".form-error");
  const submit = form.querySelector("button");
  const { start, end, quantity } = form.elements;
  let timer;
  let ticket = 0;
  const check = async () => {
    error.textContent = "";
    if (!start.value || !end.value) return;
    end.min = addDays(start.value, 1);
    if (end.value <= start.value) end.value = addDays(start.value, 1);
    const current = ++ticket;
    quote.textContent = "Consultando disponibilidad…";
    quote.className = "reserve-quote";
    try {
      const params = new URLSearchParams({
        start: start.value,
        end: end.value,
        quantity: quantity.value || 1,
      });
      const a = await api(`/tools/${tool.id}/availability?${params}`);
      if (current !== ticket) return; // llegó una consulta más reciente
      submit.disabled = !a.available;
      quote.className = "reserve-quote " + (a.available ? "ok" : "warn");
      quote.innerHTML = a.available
        ? `<strong>${a.availableUnits} ${a.availableUnits === 1 ? "libre" : "libres"}</strong> · ${a.days} ${a.days === 1 ? "día" : "días"} · Total <strong>${money(a.total)}</strong>`
        : a.availableUnits
          ? `Solo ${a.availableUnits === 1 ? "queda 1 unidad libre" : `quedan ${a.availableUnits} unidades libres`} en esas fechas.`
          : "No hay unidades libres en esas fechas. Prueba otro rango.";
    } catch (e) {
      if (current !== ticket) return;
      submit.disabled = true;
      quote.textContent = "";
      error.textContent = e.message;
    }
  };
  const schedule = () => {
    clearTimeout(timer);
    timer = setTimeout(check, 250);
  };
  for (const field of [start, end, quantity])
    field.addEventListener("input", schedule);
  form.onsubmit = async (e) => {
    e.preventDefault();
    if (!state.user) {
      $("#detail-dialog").close();
      openAuth("login");
      notify("Inicia sesión con tu cuenta de cliente para reservar.");
      return;
    }
    if (state.user.role !== "CLIENTE") {
      error.textContent = "Las reservas se hacen con una cuenta de cliente.";
      return;
    }
    submit.disabled = true;
    try {
      const r = await api("/reservations", {
        method: "POST",
        body: JSON.stringify({
          toolId: tool.id,
          start: start.value,
          end: end.value,
          quantity: Number(quantity.value),
        }),
      });
      $("#detail-dialog").close();
      notify(
        `Solicitud enviada. El proveedor tiene hasta el ${dateTime(r.respondBy)} para responder.`,
      );
      loadReservations();
      location.hash = "workspace";
    } catch (err) {
      error.textContent = err.message;
      check();
    } finally {
      submit.disabled = false;
    }
  };
  check();
}
function openAuth(mode) {
  if (state.user) {
    location.hash = "workspace";
    return;
  }
  state.mode = mode;
  const register = mode === "register";
  $("#name-field").hidden = $("#role-field").hidden = !register;
  $("#auth-form").elements.name.required = register;
  $("#auth-form").elements.password.minLength = register ? 10 : 1;
  $("#auth-form").elements.password.autocomplete = register
    ? "new-password"
    : "current-password";
  $("#auth-title").textContent = register
    ? "Empieza algo grande."
    : "Vuelve a construir.";
  $("#auth-description").textContent = register
    ? "Crea tu cuenta y prepara tu siguiente proyecto."
    : "Accede a tu espacio de trabajo.";
  $("#auth-submit").textContent = register
    ? "Crear mi cuenta →"
    : "Iniciar sesión →";
  $("#switch-auth").textContent = register
    ? "¿Ya tienes cuenta? Inicia sesión"
    : "¿Aún no tienes cuenta? Regístrate";
  $("#auth-error").textContent = "";
  if (!$("#auth-dialog").open) $("#auth-dialog").showModal();
}
function logout() {
  state.token = null;
  state.user = null;
  sessionStorage.removeItem("renttool.token");
  renderAccount();
}
function renderAccount() {
  const actions = $("#account-actions");
  $("#workspace").hidden = !state.user;
  if (!state.user) {
    actions.innerHTML =
      '<button class="text-button" data-auth="login">Iniciar sesión</button><button class="outline-button" data-auth="register">Comienza ahora</button>';
    wireAuth();
    return;
  }
  actions.innerHTML = `<a href="#workspace" class="outline-button">${esc(state.user.name.split(" ")[0])} ↗</a><button class="text-button" id="logout">Salir</button>`;
  $("#logout").onclick = () => {
    logout();
    notify("Sesión cerrada en este navegador.");
  };
  $("#workspace-title").textContent =
    state.user.role === "PROVEEDOR"
      ? "Tu equipo. Tu inventario."
      : "Tu próximo proyecto empieza aquí.";
  $("#profile-form").elements.name.value = state.user.name;
  const managesInventory = ["PROVEEDOR", "ADMINISTRADOR"].includes(
    state.user.role,
  );
  $("#new-tool").hidden =
    $("#inventory-title").hidden =
    $("#inventory").hidden =
      !managesInventory;
  $("#reservations-title").textContent = {
    CLIENTE: "Mis reservas",
    PROVEEDOR: "Solicitudes de reserva",
    ADMINISTRADOR: "Todas las reservas",
  }[state.user.role];
  loadReservations();
  if (managesInventory) loadInventory();
}
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
  const who =
    role === "CLIENTE" ? "" : ` · Cliente: ${esc(r.clientName)}`;
  const extra =
    r.status === "SOLICITADA" && r.respondBy
      ? `<p class="muted">Respuesta antes del ${dateTime(r.respondBy)}</p>`
      : r.note
        ? `<p class="muted">Motivo: ${esc(r.note)}</p>`
        : "";
  return `<article class="inventory-row reservation-row"><div><h3>${esc(r.toolName)} <span class="status-chip status-${r.status.toLowerCase()}">${reservationStatus[r.status]}</span></h3><p class="muted">${shortDate(r.start)} → ${shortDate(r.end)} · ${r.days} ${r.days === 1 ? "día" : "días"} · ${r.quantity} ${r.quantity === 1 ? "unidad" : "unidades"} · ${money(r.total)}${who}</p>${extra}</div>${actions.length ? `<div class="inventory-actions">${actions.join("")}</div>` : ""}</article>`;
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
function wireAuth() {
  document
    .querySelectorAll("[data-auth]")
    .forEach((b) => (b.onclick = () => openAuth(b.dataset.auth)));
}
async function loadInventory() {
  try {
    const data = await api("/tools?mine=true&size=100");
    $("#inventory").innerHTML = data.content.length
      ? data.content
          .map(
            (t) =>
              `<article class="inventory-row"><div><h3>${esc(t.name)}</h3><p class="muted">${money(t.dailyRate)} / día · ${t.operationalUnits} unidades operativas</p></div><div class="inventory-actions"><button class="outline-button" data-edit="${t.id}">Editar</button><button class="outline-button" data-manage="${t.id}">Unidades e imágenes</button><button class="outline-button" data-retire="${t.id}">Retirar</button></div></article>`,
          )
          .join("")
      : '<div class="empty"><h3>Tu inventario empieza contigo.</h3><p>Publica tu primera herramienta usando el botón de arriba.</p></div>';
    $("#inventory")
      .querySelectorAll("[data-edit]")
      .forEach((b) => (b.onclick = () => editTool(b.dataset.edit)));
    $("#inventory")
      .querySelectorAll("[data-manage]")
      .forEach((b) => (b.onclick = () => manageTool(b.dataset.manage)));
    $("#inventory")
      .querySelectorAll("[data-retire]")
      .forEach(
        (b) =>
          (b.onclick = async () => {
            if (
              !confirm(
                "¿Retirar esta herramienta del catálogo? Se conservará su registro.",
              )
            )
              return;
            try {
              await api("/tools/" + b.dataset.retire, { method: "DELETE" });
              notify("Herramienta retirada.");
              loadInventory();
              loadTools();
            } catch (e) {
              notify(e.message);
            }
          }),
      );
  } catch (e) {
    $("#inventory").textContent = e.message;
  }
}
async function editTool(id) {
  state.editing = id || null;
  $("#tool-form").reset();
  $("#tool-error").textContent = "";
  $("#tool-title").textContent = id
    ? "Actualiza tu equipo."
    : "Publica tu equipo.";
  if (id) {
    try {
      const t = await api("/tools/" + id);
      for (const field of ["name", "categoryId", "description", "dailyRate"])
        $("#tool-form").elements[field].value = t[field];
    } catch (e) {
      notify(e.message);
      return;
    }
  }
  $("#tool-dialog").showModal();
}
async function manageTool(id) {
  state.managing = id;
  $("#manage-error").textContent = "";
  try {
    const [t, units] = await Promise.all([
      api("/tools/" + id),
      api("/tools/" + id + "/units"),
    ]);
    $("#manage-title").textContent = t.name;
    $("#unit-list").innerHTML = units.length
      ? units
          .map(
            (u) =>
              `<div class="manage-row"><span>${esc(u.code)}</span><select aria-label="Estado de ${esc(u.code)}" data-unit="${u.id}" data-code="${esc(u.code)}">${["OPERATIVA", "MANTENIMIENTO", "RETIRADA"].map((s) => `<option ${s === u.status ? "selected" : ""}>${s}</option>`).join("")}</select></div>`,
          )
          .join("")
      : '<p class="muted">Sin unidades registradas.</p>';
    $("#unit-list")
      .querySelectorAll("select")
      .forEach(
        (s) =>
          (s.onchange = async () => {
            try {
              await api(`/tools/${id}/units/${s.dataset.unit}`, {
                method: "PUT",
                body: JSON.stringify({ code: s.dataset.code, status: s.value }),
              });
              notify("Unidad actualizada.");
              loadInventory();
              loadTools();
            } catch (e) {
              $("#manage-error").textContent = e.message;
              await manageTool(id);
            }
          }),
      );
    $("#image-list").innerHTML = t.images.length
      ? t.images
          .map(
            (i) =>
              `<div class="manage-row"><img src="${esc(i.location)}" alt="Imagen del inventario" referrerpolicy="no-referrer"><span>Imagen ${i.id}</span><button data-remove-image="${i.id}">Eliminar ×</button></div>`,
          )
          .join("")
      : '<p class="muted">Sin imágenes registradas.</p>';
    $("#image-list")
      .querySelectorAll("button")
      .forEach(
        (b) =>
          (b.onclick = async () => {
            try {
              await api(`/tools/${id}/images/${b.dataset.removeImage}`, {
                method: "DELETE",
              });
              manageTool(id);
              loadTools();
            } catch (e) {
              $("#manage-error").textContent = e.message;
            }
          }),
      );
    if (!$("#manage-dialog").open) $("#manage-dialog").showModal();
  } catch (e) {
    notify(e.message);
  }
}
$("#auth-form").onsubmit = async (event) => {
  event.preventDefault();
  const b = $("#auth-submit");
  b.disabled = true;
  $("#auth-error").textContent = "";
  const data = Object.fromEntries(new FormData(event.target));
  if (state.mode === "login") {
    delete data.name;
    delete data.role;
  }
  try {
    const result = await api(
      "/auth/" + (state.mode === "register" ? "register" : "login"),
      { method: "POST", body: JSON.stringify(data) },
    );
    state.token = result.token;
    state.user = result.user;
    sessionStorage.setItem("renttool.token", state.token);
    $("#auth-dialog").close();
    event.target.reset();
    renderAccount();
    location.hash = "workspace";
    notify("Bienvenido a RentTool.");
  } catch (e) {
    $("#auth-error").textContent = e.message;
  } finally {
    b.disabled = false;
  }
};
$("#switch-auth").onclick = () =>
  openAuth(state.mode === "login" ? "register" : "login");
$("#tool-form").onsubmit = async (e) => {
  e.preventDefault();
  const b = e.target.querySelector("button");
  b.disabled = true;
  const data = Object.fromEntries(new FormData(e.target));
  data.categoryId = Number(data.categoryId);
  data.dailyRate = Number(data.dailyRate);
  try {
    await api("/tools" + (state.editing ? "/" + state.editing : ""), {
      method: state.editing ? "PUT" : "POST",
      body: JSON.stringify(data),
    });
    $("#tool-dialog").close();
    notify("Herramienta guardada.");
    loadInventory();
    loadTools();
  } catch (error) {
    $("#tool-error").textContent = error.message;
  } finally {
    b.disabled = false;
  }
};
$("#new-tool").onclick = () => editTool();
for (const type of ["unit", "image"])
  $("#" + type + "-form").onsubmit = async (e) => {
    e.preventDefault();
    const b = e.target.querySelector("button");
    b.disabled = true;
    try {
      await api(
        `/tools/${state.managing}/${type === "unit" ? "units" : "images"}`,
        {
          method: "POST",
          body: JSON.stringify(Object.fromEntries(new FormData(e.target))),
        },
      );
      e.target.reset();
      await manageTool(state.managing);
      loadInventory();
      loadTools();
    } catch (error) {
      $("#manage-error").textContent = error.message;
    } finally {
      b.disabled = false;
    }
  };
$("#profile-form").onsubmit = async (e) => {
  e.preventDefault();
  try {
    state.user = await api("/me", {
      method: "PUT",
      body: JSON.stringify(Object.fromEntries(new FormData(e.target))),
    });
    renderAccount();
    notify("Perfil actualizado.");
  } catch (error) {
    notify(error.message);
  }
};
$("#search-form").onsubmit = (e) => {
  e.preventDefault();
  state.page = 0;
  loadTools();
};
$("#category-filter").onchange = () => {
  state.page = 0;
  loadTools();
};
$("#prev").onclick = () => {
  if (state.page > 0) {
    state.page--;
    loadTools();
  }
};
$("#next").onclick = () => {
  if (state.page + 1 < state.totalPages) {
    state.page++;
    loadTools();
  }
};
document
  .querySelectorAll("dialog .close")
  .forEach((b) => (b.onclick = () => b.closest("dialog").close()));
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
$(".menu-toggle").onclick = () => {
  const open = $("nav").classList.toggle("open");
  $(".menu-toggle").setAttribute("aria-expanded", String(open));
};
document.querySelectorAll("nav a").forEach(
  (a) =>
    (a.onclick = () => {
      $("nav").classList.remove("open");
      $(".menu-toggle").setAttribute("aria-expanded", "false");
      document
        .querySelectorAll("nav a")
        .forEach((x) => x.classList.toggle("active", x === a));
    }),
);
$("#year").textContent = new Date().getFullYear();
wireAuth();
(async () => {
  await loadCategories();
  loadTools();
  if (state.token) {
    try {
      state.user = await api("/me");
      renderAccount();
    } catch {
      logout();
    }
  }
})();

$("#upload-form").onsubmit = async (e) => {
  e.preventDefault();
  const b = e.target.querySelector("button");
  b.disabled = true;
  try {
    await api(`/tools/${state.managing}/images/upload`, {
      method: "POST",
      body: new FormData(e.target),
    });
    e.target.reset();
    await manageTool(state.managing);
    loadTools();
    notify("Imagen subida.");
  } catch (error) {
    $("#manage-error").textContent = error.message;
  } finally {
    b.disabled = false;
  }
};
