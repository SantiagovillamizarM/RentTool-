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
      sort: $("#sort-order").value,
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
      : '<div class="empty"><h3>Tu próxima herramienta está por llegar.</h3><p>No hay publicaciones para estos filtros.<br>¿Eres proveedor? Entra con la cuenta que te asignó RentTool.</p><button class="outline-button" data-empty-register>Publicar mi inventario ↗</button></div>';
    container
      .querySelectorAll("[data-detail]")
      .forEach((b) => (b.onclick = () => detail(b.dataset.detail)));
    container
      .querySelector("[data-empty-register]")
      ?.addEventListener("click", () => openAuth("login"));
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
  $("#name-field").hidden = $("#register-note").hidden = !register;
  $("#auth-form").elements.name.required = register;
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
    const field = errorField(error.message);
    if (!(field && form.elements[field] && fieldError(form, field, error.message))) {
      $("#apply-error").textContent = error.message;
      reveal($("#apply-error"), form.closest("dialog"));
    }
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
  if (state.mode === "login") delete data.name;
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
    $("#auth-error").textContent = e.message;
  } finally {
    b.disabled = false;
  }
};
$("#switch-auth").onclick = () =>
  openAuth(state.mode === "login" ? "register" : "login");
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
  await loadCategories();
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
