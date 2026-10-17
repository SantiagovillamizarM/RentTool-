"use strict";
// Panel de administración (/admin): proveedores, moderación del catálogo y reservas.
Object.assign(state, {
  providers: {},
  logoPreview: null,
  logoAction: null,
  currentLogo: null,
  editingProvider: null,
  toolPage: 0,
  removing: null,
  rejecting: null,
  toolPages: 0,
});
const MAX_LOGO_BYTES = 2 * 1024 * 1024;
const logoMarkup = (url, name) =>
  `<span class="avatar avatar-small"><img src="${esc(url || DEFAULT_LOGO)}" alt="Logo de ${esc(name)}" class="${url ? "photo" : ""}"></span>`;
function providerRow(p) {
  const f = p.profile;
  const chips = [
    p.active
      ? '<span class="status-chip status-aceptada">Activo</span>'
      : '<span class="status-chip">Inactivo</span>',
    p.mustChangePassword
      ? '<span class="status-chip status-solicitada">Clave temporal</span>'
      : "",
    f ? "" : '<span class="status-chip status-warning">Sin datos legales</span>',
  ].join(" ");
  const legal = f
    ? `NIT ${esc(f.nit)}-${f.verificationDigit} · ${personLabel[f.personType]} · ${esc(f.legalName)}`
    : "Cuenta anterior al alta por administrador. Completa sus datos o desactívala.";
  const contact = f
    ? `<p class="muted">${esc(f.city)} · ${esc(f.phone)}${f.legalRepresentative ? ` · Rep. legal: ${esc(f.legalRepresentative)}` : ""}</p>`
    : "";
  return `<article class="inventory-row reservation-row provider-row">${logoMarkup(p.logoUrl, p.name)}<div class="provider-info"><h3>${esc(p.name)} ${chips}</h3><p class="muted">${esc(p.email)} · ${legal}</p>${contact}</div><div class="inventory-actions"><button class="outline-button" data-edit-provider="${p.id}">${f ? "Editar datos" : "Completar datos"}</button><button class="outline-button" data-reset-provider="${p.id}">Clave temporal</button><button class="outline-button" data-toggle-provider="${p.id}" data-active="${p.active}">${p.active ? "Desactivar" : "Activar"}</button><button class="outline-button danger-outline" data-remove-provider="${p.id}">Eliminar</button></div></article>`;
}
async function loadProviders() {
  const box = $("#providers");
  try {
    const data = await api("/admin/providers?size=100");
    state.providers = Object.fromEntries(data.content.map((p) => [p.id, p]));
    $("#stat-providers").textContent = data.totalElements;
    box.innerHTML = data.content.length
      ? data.content.map(providerRow).join("")
      : '<div class="empty"><h3>Aún no hay proveedores.</h3><p>Registra el primero con su NIT y datos legales.</p></div>';
    box.querySelectorAll("[data-edit-provider]").forEach(
      (b) => (b.onclick = () => editProvider(b.dataset.editProvider)),
    );
    box.querySelectorAll("[data-toggle-provider]").forEach(
      (b) =>
        (b.onclick = async () => {
          const activate = b.dataset.active !== "true";
          if (
            !activate &&
            !confirm(
              "¿Desactivar este proveedor? No podrá entrar y su sesión actual se cerrará.",
            )
          )
            return;
          b.disabled = true;
          try {
            await api(`/admin/providers/${b.dataset.toggleProvider}/status`, {
              method: "PUT",
              body: JSON.stringify({ active: activate }),
            });
            notify(activate ? "Proveedor activado." : "Proveedor desactivado.");
          } catch (e) {
            notify(e.message);
          }
          loadProviders();
        }),
    );
    box
      .querySelectorAll("[data-remove-provider]")
      .forEach((b) => (b.onclick = () => openRemoval(b.dataset.removeProvider, b)));
    box.querySelectorAll("[data-reset-provider]").forEach(
      (b) =>
        (b.onclick = async () => {
          const password = prompt(
            "Nueva contraseña temporal (10 a 64 caracteres). El proveedor deberá cambiarla al entrar:",
            "",
          );
          if (password === null) return;
          try {
            await api(`/admin/providers/${b.dataset.resetProvider}/password`, {
              method: "PUT",
              body: JSON.stringify({ temporaryPassword: password }),
            });
            notify("Contraseña temporal asignada. Entrégala al proveedor.");
            loadProviders();
          } catch (e) {
            notify(e.message);
          }
        }),
    );
  } catch (e) {
    box.innerHTML = `<p class="muted">${esc(e.message)}</p>`;
  }
}
function updateProviderForm() {
  updateLegalFields($("#provider-form"));
}
function showLogo(url) {
  $("#logo-preview").src = url || DEFAULT_LOGO;
  $("#logo-preview").classList.toggle("photo", Boolean(url));
}
function resetLogo(current) {
  if (state.logoPreview) URL.revokeObjectURL(state.logoPreview);
  state.logoPreview = null;
  state.logoAction = null;
  state.currentLogo = current || null;
  showLogo(current);
  $("#logo-remove").hidden = !current;
}
$("#provider-form").elements.logo.addEventListener("change", (e) => {
  const file = e.target.files[0];
  if (!file) return;
  const form = $("#provider-form");
  if (!["image/png", "image/jpeg"].includes(file.type)) {
    e.target.value = "";
    fieldError(form, "logo", "El logo debe ser PNG o JPEG.");
    return;
  }
  if (file.size > MAX_LOGO_BYTES) {
    e.target.value = "";
    fieldError(form, "logo", "El logo supera 2 MB.");
    return;
  }
  if (state.logoPreview) URL.revokeObjectURL(state.logoPreview);
  state.logoPreview = URL.createObjectURL(file);
  state.logoAction = "upload";
  showLogo(state.logoPreview);
  $("#logo-remove").hidden = false;
});
$("#logo-remove").onclick = () => {
  const input = $("#provider-form").elements.logo;
  input.value = "";
  if (state.logoPreview) URL.revokeObjectURL(state.logoPreview);
  state.logoPreview = null;
  state.logoAction = state.currentLogo ? "remove" : null;
  showLogo(null);
  $("#logo-remove").hidden = true;
};
// Misma validación que el servidor, para señalar el campo antes de enviar.
function providerProblem(form) {
  const v = (name) => form.elements[name].value.trim();
  const creating = !state.editingProvider;
  if (creating) {
    if (!v("email")) return ["email", "Escribe el correo de acceso."];
    if (!form.elements.email.checkValidity()) return ["email", "El correo no es válido."];
    const pass = form.elements.temporaryPassword.value;
    if (pass.length < 10 || pass.length > 64)
      return ["temporaryPassword", "La contraseña temporal requiere entre 10 y 64 caracteres."];
  }
  return legalProblem(form);
}
async function saveLogo(id) {
  const form = $("#provider-form");
  if (state.logoAction === "upload") {
    const body = new FormData();
    body.append("file", form.elements.logo.files[0]);
    await api(`/admin/providers/${id}/logo`, { method: "POST", body });
  } else if (state.logoAction === "remove") {
    await api(`/admin/providers/${id}/logo`, { method: "DELETE" });
  }
}
function editProvider(id) {
  state.editingProvider = id || null;
  const form = $("#provider-form");
  form.reset();
  clearFieldErrors(form);
  $("#provider-error").textContent = "";
  resetLogo(id && state.providers[id]?.logoUrl);
  $("#provider-access").hidden = Boolean(id);
  form.elements.email.required = form.elements.temporaryPassword.required = !id;
  $("#provider-title").textContent = id
    ? "Actualiza el proveedor."
    : "Registra un proveedor.";
  $("#provider-submit").textContent = id
    ? "Guardar datos →"
    : "Registrar proveedor →";
  const profile = id && state.providers[id]?.profile;
  if (profile)
    for (const [field, value] of Object.entries(profile))
      if (form.elements[field]) form.elements[field].value = value ?? "";
  updateProviderForm();
  $("#provider-dialog").showModal();
}
$("#new-provider").onclick = () => editProvider();
for (const field of ["personType", "nit", "verificationDigit"])
  $("#provider-form").elements[field].addEventListener("input", updateProviderForm);
$("#provider-form").onsubmit = async (e) => {
  e.preventDefault();
  const form = e.target;
  clearFieldErrors(form);
  $("#provider-error").textContent = "";
  const problem = providerProblem(form);
  if (problem) {
    fieldError(form, ...problem);
    return;
  }
  const b = $("#provider-submit");
  b.disabled = true;
  const { email, temporaryPassword, logo, ...profile } = Object.fromEntries(
    new FormData(form),
  );
  profile.verificationDigit = Number(profile.verificationDigit);
  if (profile.personType === "NATURAL")
    profile.legalRepresentative = profile.representativeDocument = null;
  const id = state.editingProvider;
  let saved = null;
  try {
    saved = await api("/admin/providers" + (id ? "/" + id : ""), {
      method: id ? "PUT" : "POST",
      body: JSON.stringify(id ? profile : { email, temporaryPassword, profile }),
    });
    await saveLogo(saved.id);
    $("#provider-dialog").close();
    notify(
      id
        ? "Datos del proveedor actualizados."
        : "Proveedor registrado. Entrégale su correo y la contraseña temporal.",
    );
    loadProviders();
  } catch (error) {
    if (saved && !id) {
      // El proveedor ya existe; solo falló el logo. Se sigue en modo edición.
      await loadProviders();
      state.editingProvider = String(saved.id);
      $("#provider-access").hidden = true;
      $("#provider-title").textContent = "Actualiza el proveedor.";
      $("#provider-submit").textContent = "Guardar datos →";
      notify("Proveedor registrado, pero el logo no se pudo guardar.");
    }
    const field = errorField(error.message);
    if (!(field && fieldError(form, field, error.message))) {
      $("#provider-error").textContent = error.message;
      reveal($("#provider-error"), form.closest("dialog"));
    }
  } finally {
    b.disabled = false;
  }
};
// Cambios que el público ve en el sitio: el aviso ofrece ir a comprobarlos.
const SEE_CHANGES = { label: "Ver cambios ↗", href: "/#catalogo" };
/** Lo llama api() ante un 401: la sesión venció en el servidor. */
function logout() {
  endAdminSession("Tu sesión de administrador venció. Inicia sesión de nuevo.");
}
$("#logout").onclick = () => {
  clearSession();
  location.replace("/");
};
function renderAccount() {
  if (state.user.mustChangePassword) {
    openPassword(true);
    return;
  }
  loadApplications();
  loadProviders().then(() => {
    loadCatalog();
    loadReservations().then(countReservations);
  });
}
// Solicitudes para ser proveedor enviadas desde el sitio.
function applicationRow(r) {
  const a = r.application;
  const p = a.profile;
  const who = r.applicant ? `${esc(r.applicant.name)} · ${esc(r.applicant.email)}` : "Cuenta no disponible";
  return `<article class="inventory-row reservation-row provider-row application-row">${logoMarkup(null, a.displayName)}<div class="provider-info"><h3>${esc(a.displayName)} <span class="status-chip status-solicitada">Por revisar</span></h3><p class="muted">NIT ${esc(p.nit)}-${p.verificationDigit} · ${personLabel[p.personType]} · ${esc(p.legalName)}</p><p class="muted">Solicitante: ${who}</p><p class="muted">${esc(p.city)} · ${esc(p.address)} · ${esc(p.phone)}${p.legalRepresentative ? ` · Rep. legal: ${esc(p.legalRepresentative)} (${esc(p.representativeDocument)})` : ""}</p><p class="muted">Enviada el ${fullDateTime(a.submittedAt)}</p></div><div class="inventory-actions"><button class="button compact" data-accept-application="${a.id}" data-name="${esc(a.displayName)}">Aceptar</button><button class="outline-button danger-outline" data-reject-application="${a.id}" data-name="${esc(a.displayName)}">Rechazar</button></div></article>`;
}
async function loadApplications() {
  try {
    const data = await api("/admin/provider-applications?status=PENDIENTE&size=50");
    const count = data.totalElements;
    $("#applications-box").hidden = count === 0;
    $("#applications-count").textContent = count;
    $("#tab-applications").hidden = count === 0;
    $("#tab-applications").textContent = count;
    $("#applications").innerHTML = data.content.map(applicationRow).join("");
    $("#applications")
      .querySelectorAll("[data-accept-application]")
      .forEach(
        (b) =>
          (b.onclick = async () => {
            if (
              !confirm(
                `¿Aceptar a ${b.dataset.name} como proveedor? Su cuenta pasará a ser de proveedor y podrá publicar herramientas.`,
              )
            )
              return;
            b.disabled = true;
            try {
              await api(`/admin/provider-applications/${b.dataset.acceptApplication}/accept`, {
                method: "POST",
              });
              notify(`${b.dataset.name} ahora es proveedor. Verá la bienvenida al entrar.`);
              loadApplications();
              loadProviders();
            } catch (e) {
              notify(e.message);
              b.disabled = false;
            }
          }),
      );
    $("#applications")
      .querySelectorAll("[data-reject-application]")
      .forEach(
        (b) =>
          (b.onclick = () => {
            state.rejecting = b.dataset.rejectApplication;
            const form = $("#reject-form");
            form.reset();
            clearFieldErrors(form);
            $("#reject-error").textContent = "";
            $("#reject-title").textContent = `Rechazar a ${b.dataset.name}.`;
            $("#reject-dialog").showModal();
          }),
      );
  } catch (e) {
    $("#applications-box").hidden = false;
    $("#applications").innerHTML = `<p class="muted">${esc(e.message)}</p>`;
  }
}
$("#reject-form").onsubmit = async (e) => {
  e.preventDefault();
  const form = e.target;
  clearFieldErrors(form);
  const reason = form.elements.reason.value.trim();
  if (!reason) {
    fieldError(form, "reason", "Escribe el motivo: el solicitante lo verá.");
    return;
  }
  const button = $("#reject-submit");
  button.disabled = true;
  try {
    await api(`/admin/provider-applications/${state.rejecting}/reject`, {
      method: "POST",
      body: JSON.stringify({ reason }),
    });
    $("#reject-dialog").close();
    notify("Solicitud rechazada. El solicitante verá el motivo al entrar.");
    loadApplications();
  } catch (error) {
    $("#reject-error").textContent = error.message;
  } finally {
    button.disabled = false;
  }
};
// Pestañas del panel, sincronizadas con la URL (#proveedores, #catalogo, #reservas).
const showTab = setupTabs("proveedores");
async function countReservations() {
  try {
    const data = await api("/reservations?size=1");
    $("#stat-reservations").textContent = data.totalElements;
  } catch {}
}
const providerName = (id) => state.providers[id]?.name || `Proveedor #${id}`;
function toolRow(t) {
  return `<article class="inventory-row reservation-row provider-row"><span class="tool-thumb">${imageMarkup(t)}</span><div class="provider-info"><h3>${esc(t.name)} <span class="status-chip">${esc(t.category)}</span></h3><p class="muted">${money(t.dailyRate)} / día · ${t.operationalUnits} ${t.operationalUnits === 1 ? "unidad operativa" : "unidades operativas"} · ${esc(providerName(t.providerId))}</p></div><div class="inventory-actions"><button class="outline-button" data-edit-tool="${t.id}">Editar</button><button class="outline-button" data-retire="${t.id}" data-name="${esc(t.name)}">Retirar del catálogo</button></div></article>`;
}
async function loadCatalog() {
  const box = $("#admin-tools");
  try {
    const params = new URLSearchParams({
      q: $("#admin-search").elements.q.value,
      page: state.toolPage,
      size: 10,
    });
    const data = await api("/tools?" + params);
    state.toolPages = data.totalPages;
    if (!params.get("q")) $("#stat-tools").textContent = data.totalElements;
    box.innerHTML = data.content.length
      ? data.content.map(toolRow).join("")
      : `<div class="empty"><h3>${params.get("q") ? "Sin resultados." : "El catálogo está vacío."}</h3><p>${params.get("q") ? "Prueba con otra búsqueda." : "Cuando los proveedores publiquen, sus herramientas aparecerán aquí."}</p></div>`;
    wireImages(box);
    box
      .querySelectorAll("[data-edit-tool]")
      .forEach(
        (b) =>
          (b.onclick = () =>
            openToolEditor(b.dataset.editTool, b, {
              owner: (t) => `Proveedor: ${providerName(t.providerId)}`,
              onSaved: () => {
                notify("Herramienta actualizada en el catálogo público.", SEE_CHANGES);
                loadCatalog();
              },
            })),
      );
    box.querySelectorAll("[data-retire]").forEach(
      (b) =>
        (b.onclick = async () => {
          if (
            !confirm(
              `¿Retirar "${b.dataset.name}" del catálogo? Dejará de verse en el sitio público; su registro se conserva.`,
            )
          )
            return;
          b.disabled = true;
          try {
            await api("/tools/" + b.dataset.retire, { method: "DELETE" });
            notify("Herramienta retirada del catálogo público.", SEE_CHANGES);
            if (data.content.length === 1 && state.toolPage > 0) state.toolPage--;
            loadCatalog();
          } catch (e) {
            notify(e.message);
            b.disabled = false;
          }
        }),
    );
    $("#tools-page").textContent = data.totalPages
      ? `${state.toolPage + 1} / ${data.totalPages}`
      : "";
    $("#tools-prev").disabled = state.toolPage === 0;
    $("#tools-next").disabled = state.toolPage + 1 >= data.totalPages;
  } catch (e) {
    box.innerHTML = `<p class="muted">${esc(e.message)}</p>`;
  }
}
$("#admin-search").onsubmit = (e) => {
  e.preventDefault();
  state.toolPage = 0;
  loadCatalog();
};
$("#tools-prev").onclick = () => {
  if (state.toolPage > 0) {
    state.toolPage--;
    loadCatalog();
  }
};
$("#tools-next").onclick = () => {
  if (state.toolPage + 1 < state.toolPages) {
    state.toolPage++;
    loadCatalog();
  }
};
// Eliminar proveedor: primero se muestra lo que pasará y se pide escribir su nombre.
const plural = (n, one, many) => `${n} ${n === 1 ? one : many}`;
async function openRemoval(id, button) {
  button.disabled = true;
  try {
    const s = await api(`/admin/providers/${id}/removal`);
    state.removing = s;
    $("#remove-title").textContent = `¿Eliminar a ${s.providerName}?`;
    $("#remove-identity").textContent = s.taxId ? `NIT ${s.taxId}` : "Sin datos legales registrados";
    const effects = [
      "Su cuenta se cierra para siempre: no podrá volver a iniciar sesión.",
      s.activeTools
        ? `${plural(s.activeTools, "herramienta sale", "herramientas salen")} del catálogo público.`
        : "No tiene herramientas publicadas.",
    ];
    if (s.pendingRequests || s.upcomingRentals)
      effects.push(
        `Se cancelan ${[
          s.pendingRequests && plural(s.pendingRequests, "solicitud pendiente", "solicitudes pendientes"),
          s.upcomingRentals && plural(s.upcomingRentals, "reserva aceptada", "reservas aceptadas"),
        ]
          .filter(Boolean)
          .join(" y ")} que aún no empiezan; los clientes verán el motivo.`,
      );
    if (s.rentalsInProgress)
      // [texto destacado, resto]: los avisos se escapan siempre, el énfasis lo pone el código.
      effects.push([
        `${plural(s.rentalsInProgress, "alquiler está", "alquileres están")} en curso:`,
        `${s.rentalsInProgress === 1 ? "no se cancela y sigue" : "no se cancelan y siguen"} hasta la devolución. Coordina la entrega del equipo con el cliente.`,
      ]);
    effects.push("Sus datos se conservan en el historial y en los informes.");
    $("#remove-effects").innerHTML = effects
      .map((e) =>
        Array.isArray(e)
          ? `<li><b>${esc(e[0])}</b> ${esc(e[1])}</li>`
          : `<li>${esc(e)}</li>`,
      )
      .join("");
    $("#remove-expected").textContent = s.providerName;
    const form = $("#remove-form");
    form.reset();
    clearFieldErrors(form);
    $("#remove-error").textContent = "";
    $("#remove-submit").disabled = true;
    $("#remove-dialog").showModal();
  } catch (e) {
    notify(e.message);
  } finally {
    button.disabled = false;
  }
}
const sameName = (a, b) => a.trim().toLocaleLowerCase("es") === b.trim().toLocaleLowerCase("es");
$("#remove-form").addEventListener("input", (e) => {
  $("#remove-submit").disabled = !sameName(e.target.form.elements.confirm.value, state.removing.providerName);
});
$("#remove-form").onsubmit = async (e) => {
  e.preventDefault();
  const form = e.target;
  if (!sameName(form.elements.confirm.value, state.removing.providerName)) {
    fieldError(form, "confirm", "El nombre no coincide.");
    return;
  }
  const button = $("#remove-submit");
  button.disabled = true;
  button.textContent = "Eliminando…";
  try {
    const s = await api(`/admin/providers/${state.removing.providerId}`, { method: "DELETE" });
    $("#remove-dialog").close();
    const cancelled = s.pendingRequests + s.upcomingRentals;
    notify(
      `${s.providerName} fue eliminado. ${plural(s.activeTools, "herramienta retirada", "herramientas retiradas")}${cancelled ? ` y ${plural(cancelled, "reserva cancelada", "reservas canceladas")}` : ""}.`,
      SEE_CHANGES,
    );
    loadProviders().then(() => {
      loadCatalog();
      loadReservations().then(countReservations);
    });
  } catch (error) {
    $("#remove-error").textContent = error.message;
  } finally {
    button.textContent = "Eliminar definitivamente";
    button.disabled = !sameName(form.elements.confirm.value, state.removing.providerName);
  }
};
// Informes descargables. Las fechas son días de Bogotá, igual que en el servidor.
const REPORT_MAX_DAYS = 366;
function setRange(days) {
  const form = $("#report-form");
  const to = businessToday();
  form.elements.to.value = to;
  form.elements.from.value = addDays(to, -(days - 1));
}
function updateReportForm() {
  const form = $("#report-form");
  $("#report-status-field").hidden = form.elements.type.value !== "reservations";
  form.elements.from.max = form.elements.to.value || businessToday();
  form.elements.to.min = form.elements.from.value;
}
$("#open-report").onclick = () => {
  const form = $("#report-form");
  clearFieldErrors(form);
  $("#report-error").textContent = "";
  if (!form.elements.from.value) setRange(30);
  updateReportForm();
  $("#report-dialog").showModal();
};
$("#report-form").addEventListener("input", updateReportForm);
$("#report-form")
  .querySelectorAll("[data-days]")
  .forEach(
    (b) =>
      (b.onclick = () => {
        setRange(Number(b.dataset.days));
        updateReportForm();
      }),
  );
$("#report-form").querySelector("[data-month]").onclick = () => {
  const form = $("#report-form");
  const today = businessToday();
  form.elements.from.value = today.slice(0, 8) + "01";
  form.elements.to.value = today;
  updateReportForm();
};
const daysBetween = (from, to) =>
  Math.round((Date.parse(to) - Date.parse(from)) / 86400000);
function reportProblem(form) {
  const { from, to } = form.elements;
  if (!from.value) return ["from", "Elige la fecha inicial."];
  if (!to.value) return ["to", "Elige la fecha final."];
  if (to.value < from.value)
    return ["to", "La fecha final no puede ser anterior a la inicial."];
  if (daysBetween(from.value, to.value) >= REPORT_MAX_DAYS)
    return ["from", `El rango admite hasta ${REPORT_MAX_DAYS} días.`];
  return null;
}
/** Descarga un archivo de la API con el token de la sesión. */
async function download(path) {
  const response = await fetch("/api/v1" + path, {
    headers: { Authorization: `Bearer ${state.token}` },
  });
  if (!response.ok) {
    let detail = "";
    try {
      detail = (await response.json()).detail;
    } catch {}
    if (response.status === 401)
      endAdminSession("Tu sesión de administrador venció. Inicia sesión de nuevo.");
    throw new Error(detail || "No se pudo generar el informe.");
  }
  const disposition = response.headers.get("Content-Disposition") || "";
  const name = /filename="?([^";]+)"?/.exec(disposition)?.[1] || "informe.csv";
  const url = URL.createObjectURL(await response.blob());
  const link = Object.assign(document.createElement("a"), { href: url, download: name });
  document.body.append(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
  return name;
}
$("#report-form").onsubmit = async (e) => {
  e.preventDefault();
  const form = e.target;
  clearFieldErrors(form);
  $("#report-error").textContent = "";
  const problem = reportProblem(form);
  if (problem) {
    fieldError(form, ...problem);
    return;
  }
  const button = $("#report-submit");
  button.disabled = true;
  button.textContent = "Generando…";
  const reservations = form.elements.type.value === "reservations";
  const params = new URLSearchParams({
    from: form.elements.from.value,
    to: form.elements.to.value,
  });
  if (reservations && form.elements.status.value)
    params.set("status", form.elements.status.value);
  try {
    const name = await download(
      `/admin/reports/${reservations ? "reservations" : "reservation-events"}.csv?${params}`,
    );
    $("#report-dialog").close();
    notify(`Informe descargado: ${name}`);
  } catch (error) {
    const field = /final/i.test(error.message) ? "to" : /rango|inicial/i.test(error.message) ? "from" : null;
    if (!(field && fieldError(form, field, error.message))) {
      $("#report-error").textContent = error.message;
      reveal($("#report-error"), form.closest("dialog"));
    }
  } finally {
    button.disabled = false;
    button.textContent = "Descargar CSV ↓";
  }
};
// Solo el administrador entra aquí; cualquier otra sesión vuelve al sitio público.
(async () => {
  showTab();
  if (!state.token) {
    location.replace("/#acceso");
    return;
  }
  try {
    state.user = await api("/me");
  } catch {
    return;
  }
  if (state.user.role !== "ADMINISTRADOR") {
    location.replace("/");
    return;
  }
  watchAdminIdle();
  if (!state.token) return;
  $("#admin-main").hidden = false;
  renderAccount();
})();
