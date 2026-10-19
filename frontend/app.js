"use strict";
// Sitio público: catálogo, reservas y espacio de clientes y proveedores.
Object.assign(state, {
  categories: [],
  page: 0,
  totalPages: 0,
  mode: "login",
  afterAuth: null,
});
async function loadCategories() {
  try {
    state.categories = await api("/categories");
    $("#category-filter").innerHTML =
      '<option value="">Todas las categorías</option>' +
      state.categories.map((c) => `<option value="${c.id}">${esc(c.name)}</option>`).join("");
  } catch {
    // El catálogo muestra el error de conexión con su botón de reintentar.
  }
}
// Proveedores activos: llenan el filtro y ponen nombre a las tarjetas.
async function loadProviders() {
  try {
    const list = await api("/providers");
    state.providers = Object.fromEntries(list.map((p) => [p.id, p]));
    const select = $("#provider-filter");
    const chosen = select.value;
    select.innerHTML =
      '<option value="">Todos los proveedores</option>' +
      list.map((p) => `<option value="${p.id}">${esc(p.name)}</option>`).join("");
    select.value = state.providers[chosen] ? chosen : "";
  } catch {
    state.providers = {};
  }
}
const providerOf = (t) => state.providers?.[t.providerId]?.name;
// Una línea por sección del catálogo; las categorías nuevas simplemente no la tienen.
const SECTION_BLURBS = {
  Eléctricas: "Potencia para perforar, cortar, demoler y medir.",
  Iluminación: "Luz para trabajar de noche o en espacios cerrados.",
  Manuales: "Precisión y control, sin cables ni baterías.",
};
const EMPTY_CATALOG =
  '<div class="empty"><h3>Tu próxima herramienta está por llegar.</h3><p>No hay publicaciones para estos filtros.<br>¿Tienes equipo para alquilar? Publícalo en RentTool.</p><button class="outline-button" data-apply>Sé proveedor ↗</button></div>';
const toolsLabel = (n) => `${n} ${n === 1 ? "herramienta" : "herramientas"}`;
const unitsLabel = (n) => `${n} ${n === 1 ? "unidad operativa" : "unidades operativas"}`;
function productCard(t) {
  return `<article class="product"><div class="product-image"><span class="product-tag">${t.operationalUnits > 0 ? "EQUIPO OPERATIVO" : "CONSULTAR INVENTARIO"}</span>${imageMarkup(t)}</div><div class="product-body"><p class="product-category">${esc(t.category)}</p><h3>${esc(t.name)}</h3>${providerOf(t) ? `<p class="product-provider">Por ${esc(providerOf(t))}</p>` : ""}<p class="stock">${unitsLabel(t.operationalUnits)}</p><div class="product-bottom"><p class="price">${money(t.dailyRate)} <small>COP / día</small></p><button class="icon-button" data-detail="${t.id}" aria-label="Ver ${esc(t.name)}">↗</button></div></div></article>`;
}
function wireProducts(container) {
  container
    .querySelectorAll("[data-detail]")
    .forEach((b) => (b.onclick = () => detail(b.dataset.detail)));
  wireImages(container);
}
function catalogError(container, error) {
  container.innerHTML = `<div class="empty"><h3>No pudimos consultar el catálogo.</h3><p>${esc(error.message)}</p><button class="outline-button" id="retry-catalog">Reintentar</button></div>`;
  $("#result-count").textContent = "Conexión no disponible";
  $("#retry-catalog").onclick = async () => {
    if (!state.categories.length) await loadCategories();
    loadTools();
  };
}
/**
 * Sin búsqueda ni categoría, el catálogo se divide en secciones (una por categoría). Al buscar o
 * filtrar se vuelve a la cuadrícula paginada.
 */
let catalogTicket = 0;
async function loadTools() {
  if (!validateAvailabilityFilters()) return;
  const ticket = ++catalogTicket;
  const grouped = !$("#search").value.trim() && !$("#category-filter").value && !$("#available-start").value;
  $("#catalog-sections").hidden = !grouped;
  $("#products").hidden = grouped;
  $(".catalog .pagination").hidden = grouped;
  return grouped ? loadSections(ticket) : loadGrid(ticket);
}
async function loadSections(ticket) {
  const box = $("#catalog-sections");
  box.innerHTML = '<div class="empty">Consultando herramientas…</div>';
  try {
    if (!state.categories.length) await loadCategories();
    if (!state.categories.length) throw new Error("No se pudieron cargar las secciones.");
    const filter = new URLSearchParams({ size: 100, sort: $("#sort-order").value });
    if ($("#provider-filter").value) filter.set("providerId", $("#provider-filter").value);
    const results = await Promise.all(
      state.categories.map((c) => api(`/tools?categoryId=${c.id}&${filter}`)),
    );
    if (ticket !== catalogTicket) return;
    const sections = state.categories
      .map((c, i) => ({ category: c, page: results[i] }))
      .filter((s) => s.page.totalElements > 0);
    const total = sections.reduce((n, s) => n + s.page.totalElements, 0);
    $("#result-count").textContent = `${toolsLabel(total)} en ${sections.length} ${sections.length === 1 ? "sección" : "secciones"}`;
    box.innerHTML = sections.length
      ? sections
          .map(
            ({ category: c, page }) =>
              `<section class="catalog-group" aria-labelledby="seccion-${c.id}"><header class="catalog-group-head"><span class="catalog-group-icon" aria-hidden="true">${esc(c.icon)}</span><div class="catalog-group-title"><h3 id="seccion-${c.id}">${esc(c.name)}</h3><p class="muted">${esc(SECTION_BLURBS[c.name] || "")}</p></div><span class="catalog-group-count">${toolsLabel(page.totalElements)}</span><button class="text-button" data-only-category="${c.id}">Ver solo ${esc(c.name)} →</button></header><div class="products">${page.content.map(productCard).join("")}</div></section>`,
          )
          .join("")
      : EMPTY_CATALOG;
    box.querySelectorAll("[data-only-category]").forEach(
      (b) =>
        (b.onclick = () => {
          $("#category-filter").value = b.dataset.onlyCategory;
          state.page = 0;
          loadTools();
          $("#catalogo").scrollIntoView({ behavior: reducedMotion.matches ? "auto" : "smooth" });
        }),
    );
    wireProducts(box);
  } catch (error) {
    if (ticket === catalogTicket) catalogError(box, error);
  }
}
async function loadGrid(ticket) {
  const container = $("#products");
  container.innerHTML = '<div class="empty">Consultando herramientas…</div>';
  try {
    const params = new URLSearchParams({
      q: $("#search").value,
      page: state.page,
      size: 6,
      sort: $("#sort-order").value,
    });
    if ($("#category-filter").value)
      params.set("categoryId", $("#category-filter").value);
    if ($("#provider-filter").value)
      params.set("providerId", $("#provider-filter").value);
    if ($("#available-start").value) {
      params.set("start", $("#available-start").value);
      params.set("end", $("#available-end").value);
      params.set("quantity", $("#available-quantity").value);
    }
    const data = await api("/tools?" + params);
    if (ticket !== catalogTicket) return;
    state.totalPages = data.totalPages;
    const n = data.totalElements;
    $("#result-count").textContent = `${n} ${n === 1 ? "herramienta encontrada" : "herramientas encontradas"}`;
    container.innerHTML = data.content.length ? data.content.map(productCard).join("") : EMPTY_CATALOG;
    wireProducts(container);
    $("#page-label").textContent =
      `${data.totalPages ? state.page + 1 : 0} / ${data.totalPages}`;
    $("#prev").disabled = state.page === 0;
    $("#next").disabled = state.page + 1 >= data.totalPages;
  } catch (error) {
    if (ticket !== catalogTicket) return;
    catalogError(container, error);
    $("#prev").disabled = $("#next").disabled = true;
  }
}
async function detail(id) {
  try {
    const t = await api("/tools/" + id);
    const start = $("#available-start").value || addDays(businessToday(), 1);
    const end = $("#available-end").value || addDays(start, 2);
    const quantity = $("#available-start").value ? $("#available-quantity").value : "1";
    $("#detail-content").innerHTML =
      `<div class="detail-image">${imageMarkup(t)}</div><div class="detail-summary"><p class="eyebrow">${esc(t.category)}</p><h2>${esc(t.name)}</h2>${providerOf(t) ? `<p class="detail-provider">Ofrecido por <strong>${esc(providerOf(t))}</strong></p>` : ""}<p class="muted">${esc(t.description)}</p><p class="detail-price">${money(t.dailyRate)} <small>COP / día</small></p><p class="stock">${unitsLabel(t.operationalUnits)}</p></div>` +
      `<form id="reserve-form" class="reserve-form" novalidate><h3>Reserva por fechas</h3><div class="reserve-fields"><label>Entrega<input type="date" name="start" required min="${businessToday()}" value="${start}"></label><label>Devolución<input type="date" name="end" required min="${addDays(start, 1)}" value="${end}"></label><label>Unidades<input type="number" name="quantity" required min="1" max="20" value="${quantity}"></label></div><p class="reserve-quote" aria-live="polite"></p><p class="form-error" role="alert"></p><button class="button full">Solicitar reserva →</button><p class="muted reserve-hint">El día de devolución no se cobra. El proveedor tiene 24 horas para aceptar.</p></form>`;
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
    const current = ++ticket;
    submit.disabled = true;
    if (!start.value || !end.value) { quote.textContent = "Indica ambas fechas."; return; }
    end.min = addDays(start.value, 1);
    if (end.value <= start.value) end.value = addDays(start.value, 1);
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
      showFormError(form, e, error);
    }
  };
  const schedule = () => {
    ticket++;
    submit.disabled = true;
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
    // El proveedor llama al cliente para acordar el pago y la entrega.
    if (!state.user.phone) {
      $("#detail-dialog").close();
      location.hash = "workspace";
      const phone = $("#profile-form").elements.phone;
      fieldError($("#profile-form"), "phone", "Agrega tu teléfono para que el proveedor pueda contactarte.");
      phone.focus();
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
      showFormError(form, err, error);
    } finally {
      submit.disabled = false;
    }
  };
  form.closest("dialog").addEventListener("close", () => { clearTimeout(timer); ticket++; }, { once: true });
  check();
}
function openAuth(mode) {
  if (isAdministrator()) {
    location.href = "/admin";
    return;
  }
  if (isProvider()) {
    location.href = "/proveedor";
    return;
  }
  if (state.user) {
    location.hash = "workspace";
    return;
  }
  state.mode = mode;
  const register = mode === "register";
  $("#name-field").hidden = $("#phone-field").hidden = $("#register-note").hidden = !register;
  $("#auth-form").elements.name.required = register;
  $("#auth-form").elements.phone.required = register;
  $("#auth-form").elements.password.minLength = register ? 10 : 1;
  $("#auth-form").elements.password.autocomplete = register
    ? "new-password"
    : "current-password";
  $("#auth-title").textContent = register
    ? "Empieza algo grande."
    : "Vuelve a construir.";
  $("#auth-description").textContent = register
    ? "Crea tu cuenta de cliente y prepara tu siguiente proyecto."
    : "Accede a tu espacio de trabajo.";
  $("#auth-submit").textContent = register
    ? "Crear mi cuenta →"
    : "Iniciar sesión →";
  $("#switch-auth").textContent = register
    ? "¿Ya tienes cuenta? Inicia sesión"
    : "¿Aún no tienes cuenta? Regístrate";
  $("#auth-error").textContent = "";
  $("#auth-notice").hidden = true;
  if (!$("#auth-dialog").open) $("#auth-dialog").showModal();
}
function logout() {
  clearSession();
  renderAccount();
}
const isAdministrator = () => state.user?.role === "ADMINISTRADOR";
const isProvider = () => state.user?.role === "PROVEEDOR";
// Administrador y proveedor trabajan en su panel; aquí solo ven el sitio público.
const PANELS = {
  ADMINISTRADOR: {
    href: "/admin",
    bar: "Estás viendo el sitio público como administrador.",
    back: "← Volver al panel de administración",
    button: "Panel de administración ↗",
  },
  PROVEEDOR: {
    href: "/proveedor",
    bar: "Así ven los clientes tu catálogo.",
    back: "← Volver a tu panel de proveedor",
    button: "Panel de proveedor ↗",
  },
};
function renderAccount() {
  const actions = $("#account-actions");
  const panel = PANELS[state.user?.role];
  $("#admin-bar").hidden = !panel;
  $("#workspace").hidden = !state.user || Boolean(panel);
  if (panel) {
    if (isAdministrator()) watchAdminIdle();
    $("#role-bar-text").textContent = panel.bar;
    $("#role-bar-link").textContent = panel.back;
    $("#role-bar-link").href = panel.href;
    actions.innerHTML = `<a href="${panel.href}" class="outline-button">${panel.button}</a><button class="text-button" id="logout">Salir</button>`;
    $("#logout").onclick = () => {
      logout();
      notify("Sesión cerrada en este navegador.");
    };
    if (state.user.mustChangePassword) openPassword(true);
    return;
  }
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
  $("#workspace-title").textContent = "Tu próximo proyecto empieza aquí.";
  $("#profile-form").elements.name.value = state.user.name;
  $("#profile-form").elements.phone.value = state.user.phone || "";
  $("#profile-avatar").src = "assets/icons/user.png";
  $("#profile-avatar").classList.remove("photo");
  $("#reservations-title").textContent = "Mis reservas";
  loadReservations();
  renderProviderCta();
}
// Solicitud para ser proveedor: estado visible en el espacio del cliente.
async function renderProviderCta() {
  const cta = $("#provider-cta");
  try {
    state.application = await api("/me/provider-application");
  } catch {
    state.application = null;
  }
  const pending = state.application?.status === "PENDIENTE";
  cta.hidden = false;
  cta.classList.toggle("is-pending", pending);
  $("#provider-cta-title").textContent = pending
    ? "Tu solicitud de proveedor está en revisión."
    : "¿Tienes herramientas para alquilar?";
  $("#provider-cta-text").textContent = pending
    ? `Enviada el ${fullDateTime(state.application.submittedAt)}. Te avisaremos aquí cuando el equipo de RentTool responda.`
    : "Solicita ser proveedor y publica tu inventario en RentTool.";
  cta.querySelector("[data-apply]").hidden = pending;
}
async function startApplication() {
  if (!state.user) {
    state.afterAuth = "apply";
    openAuth("register");
    $("#auth-notice").textContent =
      "Primero crea tu cuenta o inicia sesión; después completas los datos de tu empresa.";
    $("#auth-notice").hidden = false;
    return;
  }
  if (isProvider()) {
    location.href = "/proveedor";
    return;
  }
  if (isAdministrator()) return;
  try {
    state.application = await api("/me/provider-application");
  } catch (e) {
    notify(e.message);
    return;
  }
  if (state.application?.status === "PENDIENTE") {
    notify("Tu solicitud ya está en revisión. Te avisaremos aquí cuando haya respuesta.");
    return;
  }
  openApplicationForm(state.application?.status === "RECHAZADA" ? state.application.profile : null);
}
function openApplicationForm(previous) {
  const form = $("#apply-form");
  form.reset();
  clearFieldErrors(form);
  $("#apply-error").textContent = "";
  if (previous)
    for (const [field, value] of Object.entries(previous))
      if (form.elements[field]) form.elements[field].value = value ?? "";
  updateLegalFields(form);
  $("#apply-dialog").showModal();
}
document.addEventListener("click", (e) => {
  if (e.target.closest?.("[data-apply]")) startApplication();
});
$("#apply-form").addEventListener("input", (e) => {
  if (["personType", "nit", "verificationDigit"].includes(e.target.name))
    updateLegalFields(e.currentTarget);
});
$("#apply-form").onsubmit = async (e) => {
  e.preventDefault();
  const form = e.target;
  clearFieldErrors(form);
  $("#apply-error").textContent = "";
  const problem = legalProblem(form);
  if (problem) {
    fieldError(form, ...problem);
    return;
  }
  if (!form.elements.consent.checked) {
    fieldError(form, "consent", "Confirma que entiendes el cambio de tu cuenta.");
    return;
  }
  const button = $("#apply-submit");
  button.disabled = true;
  try {
    await api("/me/provider-application", {
      method: "POST",
      body: JSON.stringify(legalPayload(form)),
    });
    $("#apply-dialog").close();
    notify("Solicitud enviada. Te avisaremos aquí cuando el equipo de RentTool la revise.");
    renderProviderCta();
  } catch (error) {
    showFormError(form, error, $("#apply-error"));
  } finally {
    button.disabled = false;
  }
};
// Respuesta del administrador: se muestra una sola vez al volver a entrar.
async function showApplicationNotice() {
  let application;
  try {
    application = await api("/me/provider-application");
  } catch {
    return false;
  }
  if (!application?.needsNotice) return false;
  const accepted = application.status === "ACEPTADA";
  $("#notice-title").textContent = accepted
    ? `¡Bienvenido, ${application.displayName}!`
    : "Se rechazó tu solicitud de proveedor.";
  $("#notice-text").textContent = accepted
    ? "Tu solicitud fue aceptada. Ya puedes publicar tus herramientas y recibir reservas desde tu panel."
    : "El equipo de RentTool revisó tus datos y no pudo aprobar la solicitud por ahora.";
  $("#notice-reason").hidden = accepted;
  $("#notice-reason").textContent = accepted ? "" : `Motivo: ${application.rejectionReason}`;
  $("#notice-primary").textContent = accepted ? "Ingresar al panel proveedor →" : "Entendido";
  $("#notice-secondary").hidden = accepted;
  $("#notice-secondary").textContent = "Enviar una nueva solicitud";
  const acknowledge = () =>
    api("/me/provider-application/acknowledge", { method: "POST" }).catch(() => {});
  $("#notice-primary").onclick = async () => {
    await acknowledge();
    if (accepted) location.href = "/proveedor";
    else $("#notice-dialog").close();
  };
  $("#notice-secondary").onclick = async () => {
    await acknowledge();
    $("#notice-dialog").close();
    openApplicationForm(application.profile);
  };
  $("#notice-dialog").addEventListener("close", acknowledge, { once: true });
  $("#notice-dialog").showModal();
  return true;
}
function wireAuth() {
  document
    .querySelectorAll("[data-auth]")
    .forEach((b) => (b.onclick = () => openAuth(b.dataset.auth)));
}
$("#auth-form").onsubmit = async (event) => {
  event.preventDefault();
  const b = $("#auth-submit");
  b.disabled = true;
  $("#auth-error").textContent = "";
  const data = Object.fromEntries(new FormData(event.target));
  if (state.mode === "login") {
    delete data.name;
    delete data.phone;
  }
  try {
    const result = await api(
      "/auth/" + (state.mode === "register" ? "register" : "login"),
      { method: "POST", body: JSON.stringify(data) },
    );
    saveSession(result);
    state.user = result.user;
    sessionStorage.setItem("renttool.activity", String(Date.now()));
    if (isAdministrator()) {
      location.href = "/admin";
      return;
    }
    $("#auth-dialog").close();
    event.target.reset();
    // Un proveedor recién aceptado ve primero la bienvenida; si no, va directo a su panel.
    if (isProvider()) {
      if (!(await showApplicationNotice())) location.href = "/proveedor";
      else renderAccount();
      return;
    }
    renderAccount();
    location.hash = "workspace";
    const noticed = await showApplicationNotice();
    if (state.afterAuth === "apply" && !noticed) startApplication();
    else if (!state.user.mustChangePassword && !noticed) notify("Bienvenido a RentTool.");
    state.afterAuth = null;
  } catch (e) {
    showFormError(event.target, e, $("#auth-error"));
  } finally {
    b.disabled = false;
  }
};
$("#switch-auth").onclick = () =>
  openAuth(state.mode === "login" ? "register" : "login");
$("#profile-form").onsubmit = async (e) => {
  e.preventDefault();
  clearFieldErrors(e.target);
  try {
    state.user = await api("/me", {
      method: "PUT",
      body: JSON.stringify(Object.fromEntries(new FormData(e.target))),
    });
    renderAccount();
    notify("Perfil actualizado.");
  } catch (error) {
    if (!showFormError(e.target, error)) notify(error.message);
  }
};

function validateAvailabilityFilters() {
  const start = $("#available-start"), end = $("#available-end"), quantity = $("#available-quantity");
  start.min = businessToday();
  end.min = start.value ? addDays(start.value, 1) : businessToday();
  start.required = Boolean(end.value);
  end.required = Boolean(start.value);
  quantity.setCustomValidity(!start.value && quantity.value !== "1" ? "Indica las fechas para buscar por cantidad disponible." : "");
  for (const input of [start, end, quantity]) if (!input.reportValidity()) return false;
  return true;
}
for (const id of ["available-start", "available-end", "available-quantity"]) {
  $("#" + id).addEventListener("change", () => {
    $("#available-end").min = $("#available-start").value ? addDays($("#available-start").value, 1) : businessToday();
    if (Boolean($("#available-start").value) !== Boolean($("#available-end").value)) return;
    state.page = 0; loadTools();
  });
}
$("#clear-availability").onclick = () => {
  $("#available-start").value = $("#available-end").value = "";
  $("#available-quantity").value = "1";
  state.page = 0;
  loadTools();
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
$("#provider-filter").onchange = () => {
  state.page = 0;
  loadTools();
};
$("#sort-order").onchange = () => {
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
  await Promise.all([loadCategories(), loadProviders()]);
  loadTools();
  if (state.token) {
    try {
      state.user = await api("/me");
      await syncSessionRole();
      renderAccount();
      showApplicationNotice();
    } catch {
      logout();
    }
  }
  // El panel de administración envía aquí a quien llega sin sesión.
  if (location.hash === "#acceso" && !state.user) {
    history.replaceState(null, "", location.pathname);
    openAuth("login");
    const notice = sessionStorage.getItem("renttool.notice");
    sessionStorage.removeItem("renttool.notice");
    $("#auth-notice").textContent = notice || "";
    $("#auth-notice").hidden = !notice;
  }
})();
