"use strict";
// Panel del proveedor (/proveedor): inventario, solicitudes de reserva y datos de su empresa.
Object.assign(state, { managing: null });
const SEE_CATALOG = { label: "Ver en el catálogo ↗", href: "/#catalogo" };
const showTab = setupTabs("inventario");
const unitsLabel = (n) => `${n} ${n === 1 ? "unidad operativa" : "unidades operativas"}`;

/** Lo llama api() ante un 401: la sesión venció o la cuenta ya no está disponible. */
function logout() {
  clearSession();
  location.replace("/#acceso");
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
  $("#provider-name").value = state.user.name;
  $("#provider-title").textContent = `${state.user.name}, tu inventario.`;
  $("#profile-avatar").src = state.user.logoUrl || DEFAULT_LOGO;
  $("#profile-avatar").classList.toggle("photo", Boolean(state.user.logoUrl));
  loadInventory();
  loadReservations();
  loadCompany();
}

// Inventario: lo que el proveedor publica en el catálogo.
function inventoryRow(t) {
  const warning = t.operationalUnits
    ? ""
    : ' <span class="status-chip status-warning">Sin unidades: no se puede reservar</span>';
  return `<article class="inventory-row reservation-row provider-row"><span class="tool-thumb">${imageMarkup(t)}</span><div class="provider-info"><h3>${esc(t.name)} <span class="status-chip">${esc(t.category)}</span>${warning}</h3><p class="muted">${money(t.dailyRate)} / día · ${unitsLabel(t.operationalUnits)}</p></div><div class="inventory-actions"><button class="outline-button" data-edit="${t.id}">Editar</button><button class="outline-button" data-manage="${t.id}">Unidades e imágenes</button><button class="outline-button" data-retire="${t.id}" data-name="${esc(t.name)}">Retirar</button></div></article>`;
}
async function loadInventory() {
  const box = $("#inventory");
  try {
    const data = await api("/tools?mine=true&size=100");
    $("#stat-tools").textContent = data.totalElements;
    box.innerHTML = data.content.length
      ? data.content.map(inventoryRow).join("")
      : '<div class="empty"><h3>Tu inventario empieza contigo.</h3><p>Publica tu primera herramienta con el botón de arriba.</p></div>';
    wireImages(box);
    box
      .querySelectorAll("[data-edit]")
      .forEach(
        (b) =>
          (b.onclick = () =>
            openToolEditor(b.dataset.edit, b, {
              units: true,
              onSaved: () => {
                notify("Herramienta actualizada.", SEE_CATALOG);
                loadInventory();
              },
            })),
      );
    box.querySelectorAll("[data-manage]").forEach((b) => (b.onclick = () => manageTool(b.dataset.manage)));
    box.querySelectorAll("[data-retire]").forEach(
      (b) =>
        (b.onclick = async () => {
          if (!(await confirmAction({
            title: `¿Retirar «${b.dataset.name}»?`,
            message: "Dejará de aparecer en el catálogo y no admitirá nuevas solicitudes. Su registro y las reservas existentes se conservan.",
            confirmLabel: "Retirar herramienta", danger: true,
          }))) return;
          b.disabled = true;
          try {
            await api("/tools/" + b.dataset.retire, { method: "DELETE" });
            notify("Herramienta retirada del catálogo.", SEE_CATALOG);
            loadInventory();
          } catch (e) {
            notify(e.message);
            b.disabled = false;
          }
        }),
    );
  } catch (e) {
    box.innerHTML = `<p class="muted">${esc(e.message)}</p>`;
  }
}
$("#new-tool").onclick = (e) =>
  openToolEditor(null, e.currentTarget, {
    units: true,
    onSaved: () => {
      loadInventory();
      notify("Herramienta publicada.", SEE_CATALOG);
    },
  });

// Unidades físicas e imágenes adicionales de una herramienta.
const UNIT_STATUS = { OPERATIVA: "Operativa", MANTENIMIENTO: "Mantenimiento", RETIRADA: "Retirada" };
function unitCard(u) {
  const options = Object.entries(UNIT_STATUS)
    .map(([value, label]) => `<option value="${value}" ${value === u.status ? "selected" : ""}>${label}</option>`)
    .join("");
  return `<div class="unit-card is-${u.status.toLowerCase()}"><span class="unit-code"><span class="unit-dot is-${u.status.toLowerCase()}"></span>${esc(u.code)}</span><select aria-label="Estado de ${esc(u.code)}" data-unit="${u.id}" data-code="${esc(u.code)}" data-status="${u.status}">${options}</select></div>`;
}
const UPLOAD_PROMPT = "Elegir una imagen";
$("#upload-form").elements.file.addEventListener("change", (e) => {
  $("#upload-name").textContent = e.target.files[0]?.name || UPLOAD_PROMPT;
});
async function manageTool(id) {
  state.managing = id;
  $("#manage-error").textContent = "";
  try {
    const [t, units] = await Promise.all([api("/tools/" + id), api("/tools/" + id + "/units")]);
    $("#manage-title").textContent = t.name;
    const current = units.filter((u) => u.status !== "RETIRADA");
    const retired = units.filter((u) => u.status === "RETIRADA");
    const working = current.filter((u) => u.status === "OPERATIVA").length;
    const repairing = current.length - working;
    $("#unit-summary").innerHTML =
      `<span class="unit-dot is-operativa"></span>${working} ${working === 1 ? "operativa" : "operativas"}` +
      (repairing ? `<span class="unit-dot is-mantenimiento"></span>${repairing} en mantenimiento` : "");
    $("#unit-list").innerHTML =
      (current.length
        ? `<div class="unit-grid">${current.map(unitCard).join("")}</div>`
        : '<p class="manage-empty">Sin unidades: nadie puede reservarla. Agrega al menos una operativa.</p>') +
      (retired.length
        ? `<details class="unit-retired"><summary>${retired.length} ${retired.length === 1 ? "retirada" : "retiradas"}</summary><div class="unit-grid">${retired.map(unitCard).join("")}</div></details>`
        : "");
    $("#unit-list")
      .querySelectorAll("select")
      .forEach(
        (s) =>
          (s.onchange = async () => {
            const next = s.value;
            const previous = s.dataset.status;
            if (next !== "OPERATIVA" && !(await confirmAction({
              title: `¿Cambiar el estado de ${s.dataset.code}?`,
              message: `Pasará a ${next === "RETIRADA" ? "retirada" : "mantenimiento"} y dejará de contarse como operativa para nuevas asignaciones. Las reservas existentes no se cancelan automáticamente.`,
              confirmLabel: "Cambiar estado", danger: next === "RETIRADA",
            }))) { s.value = previous; return; }
            s.disabled = true;
            try {
              await api(`/tools/${id}/units/${s.dataset.unit}`, {
                method: "PUT",
                body: JSON.stringify({ code: s.dataset.code, status: next }),
              });
              s.dataset.status = next;
              notify("Unidad actualizada.");
              loadInventory();
            } catch (e) {
              await manageTool(id);
              $("#manage-error").textContent = e.message;
            } finally {
              s.disabled = false;
            }
          }),
      );
    $("#image-summary").textContent = `${t.images.length} ${t.images.length === 1 ? "imagen" : "imágenes"}`;
    $("#image-list").innerHTML = t.images.length
      ? `<div class="image-grid">${t.images
          .map(
            (i, n) =>
              `<figure class="image-card"><img src="${esc(i.location)}" alt="Imagen ${n + 1}" referrerpolicy="no-referrer"><figcaption>${n === 0 ? '<span class="image-main">Principal</span>' : `Imagen ${n + 1}`}</figcaption><button type="button" class="image-remove" data-remove-image="${i.id}" aria-label="Eliminar imagen ${n + 1}" title="Eliminar">×</button></figure>`,
          )
          .join("")}</div>`
      : '<p class="manage-empty">Sin imágenes: el catálogo muestra la ilustración predeterminada.</p>';
    $("#image-list")
      .querySelectorAll("button")
      .forEach(
        (b) =>
          (b.onclick = async () => {
            if (!(await confirmAction({
              title: `¿Eliminar esta imagen de «${t.name}»?`,
              message: "La imagen dejará de mostrarse en la publicación. Si era la principal, se mostrará la siguiente imagen o la ilustración predeterminada.",
              confirmLabel: "Eliminar imagen", danger: true,
            }))) return;
            b.disabled = true;
            try {
              await api(`/tools/${id}/images/${b.dataset.removeImage}`, { method: "DELETE" });
              await manageTool(id);
              loadInventory();
            } catch (e) {
              $("#manage-error").textContent = e.message;
              b.disabled = false;
            }
          }),
      );
    if (!$("#manage-dialog").open) $("#manage-dialog").showModal();
  } catch (e) {
    notify(e.message);
  }
}
for (const type of ["unit", "image"])
  $("#" + type + "-form").onsubmit = async (e) => {
    e.preventDefault();
    const b = e.target.querySelector("button");
    b.disabled = true;
    try {
      await api(`/tools/${state.managing}/${type === "unit" ? "units" : "images"}`, {
        method: "POST",
        body: JSON.stringify(Object.fromEntries(new FormData(e.target))),
      });
      e.target.reset();
      await manageTool(state.managing);
      loadInventory();
    } catch (error) {
      showFormError(e.target, error, $("#manage-error"));
    } finally {
      b.disabled = false;
    }
  };
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
    $("#upload-name").textContent = UPLOAD_PROMPT;
    await manageTool(state.managing);
    loadInventory();
    notify("Imagen subida.");
  } catch (error) {
    showFormError(e.target, error, $("#manage-error"), "file");
  } finally {
    b.disabled = false;
  }
};

// Solicitudes: cuántas esperan respuesta y cuántas reservas están aceptadas. Se recalcula cada vez
// que la lista se recarga, también tras aceptar o rechazar.
document.addEventListener("reservations:loaded", (e) => {
  const reservations = e.detail.content;
  const pending = reservations.filter((r) => r.status === "SOLICITADA").length;
  $("#stat-pending").textContent = pending;
  $("#stat-active").textContent = reservations.filter((r) => r.status === "ACEPTADA" && r.rentalStatus !== "DEVUELTA").length;
  $("#tab-pending").hidden = pending === 0;
  $("#tab-pending").textContent = pending;
});

// Mi empresa: el proveedor actualiza sus datos y su logo; NIT y tipo de persona son del administrador.
const MAX_LOGO_BYTES = 2 * 1024 * 1024;
const company = { profile: null, logoAction: null, preview: null };
async function loadCompany() {
  const box = $("#company-data");
  $("#edit-company").hidden = true;
  try {
    const p = await api("/me/provider-profile");
    company.profile = p;
    $("#edit-company").hidden = false;
    const rows = [
      ["Nombre comercial", p.tradeName || p.legalName],
      ["Razón social o nombre", p.legalName],
      ["NIT", `${p.nit}-${p.verificationDigit}`],
      ["Tipo de persona", personLabel[p.personType]],
      ["Representante legal", p.legalRepresentative ? `${p.legalRepresentative} · ${p.representativeDocument}` : null],
      ["Teléfono", p.phone],
      ["Dirección", p.address],
      ["Ciudad", p.city],
      ["Correo de acceso", state.user.email],
    ].filter(([, value]) => value);
    box.innerHTML = rows.map(([k, value]) => `<div><dt>${esc(k)}</dt><dd>${esc(value)}</dd></div>`).join("");
  } catch (e) {
    box.innerHTML = `<p class="muted">${e.status === 404 ? "Tus datos legales aún no están registrados. El administrador los completará." : esc(e.message)}</p>`;
  }
}

function showCompanyLogo(url) {
  $("#company-logo").src = url || DEFAULT_LOGO;
  $("#company-logo").classList.toggle("photo", Boolean(url));
  $("#company-logo-remove").hidden = !url;
}
function openCompany() {
  const form = $("#company-form");
  const p = company.profile;
  form.reset();
  clearFieldErrors(form);
  $("#company-error").textContent = "";
  for (const field of ["legalName", "tradeName", "legalRepresentative", "representativeDocument", "phone", "address", "city"])
    form.elements[field].value = p[field] ?? "";
  $("#company-identity").textContent = `${personLabel[p.personType]} · NIT ${p.nit}-${p.verificationDigit}`;
  form.querySelector(".legal-rep").hidden = p.personType !== "JURIDICA";
  if (company.preview) URL.revokeObjectURL(company.preview);
  company.preview = company.logoAction = null;
  showCompanyLogo(state.user.logoUrl);
  $("#company-dialog").showModal();
}
$("#edit-company").onclick = openCompany;
$("#company-form").elements.logo.addEventListener("change", (e) => {
  const file = e.target.files[0];
  if (!file) return;
  if (!["image/png", "image/jpeg"].includes(file.type) || file.size > MAX_LOGO_BYTES) {
    e.target.value = "";
    fieldError($("#company-form"), "logo", "El logo debe ser PNG o JPEG de hasta 2 MB.");
    return;
  }
  if (company.preview) URL.revokeObjectURL(company.preview);
  company.preview = URL.createObjectURL(file);
  company.logoAction = "upload";
  showCompanyLogo(company.preview);
});
$("#company-logo-remove").onclick = () => {
  $("#company-form").elements.logo.value = "";
  if (company.preview) URL.revokeObjectURL(company.preview);
  company.preview = null;
  company.logoAction = state.user.logoUrl ? "remove" : null;
  showCompanyLogo(null);
};
function companyProblem(form) {
  const v = (name) => form.elements[name].value.trim();
  if (!v("legalName")) return ["legalName", "Escribe la razón social o el nombre completo."];
  if (company.profile.personType === "JURIDICA") {
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
$("#company-form").onsubmit = async (e) => {
  e.preventDefault();
  const form = e.target;
  clearFieldErrors(form);
  $("#company-error").textContent = "";
  const problem = companyProblem(form);
  if (problem) {
    fieldError(form, ...problem);
    return;
  }
  const f = (name) => form.elements[name].value.trim();
  const legalEntity = company.profile.personType === "JURIDICA";
  const b = $("#company-submit");
  b.disabled = true;
  try {
    await api("/me/provider-profile", {
      method: "PUT",
      body: JSON.stringify({
        legalName: f("legalName"),
        tradeName: f("tradeName") || null,
        legalRepresentative: legalEntity ? f("legalRepresentative") : null,
        representativeDocument: legalEntity ? f("representativeDocument") : null,
        phone: f("phone"),
        address: f("address"),
        city: f("city"),
      }),
    });
    if (company.logoAction === "upload") {
      const body = new FormData();
      body.append("file", form.elements.logo.files[0]);
      await api("/me/provider-profile/logo", { method: "POST", body });
    } else if (company.logoAction === "remove") {
      await api("/me/provider-profile/logo", { method: "DELETE" });
    }
    $("#company-dialog").close();
    state.user = await api("/me");
    renderAccount();
    notify("Datos de tu empresa actualizados.", SEE_CATALOG);
  } catch (error) {
    showFormError(form, error, $("#company-error"), /logo|imagen/i.test(error.message) ? "logo" : null);
  } finally {
    b.disabled = false;
  }
};

// Solo el proveedor entra aquí; cualquier otra sesión vuelve al sitio público.
(async () => {
  showTab();
  if (!state.token) {
    location.replace("/#acceso");
    return;
  }
  try {
    state.user = await api("/me");
    await syncSessionRole();
  } catch {
    return;
  }
  if (state.user.role !== "PROVEEDOR") {
    location.replace(state.user.role === "ADMINISTRADOR" ? "/admin" : "/");
    return;
  }
  $("#admin-main").hidden = false;
  renderAccount();
  // Si llega directo tras ser aceptado, la bienvenida se muestra aquí una vez.
  try {
    const application = await api("/me/provider-application");
    if (application?.needsNotice && application.status === "ACEPTADA") {
      notify(`Tu solicitud fue aceptada. ¡Bienvenido, ${application.displayName}!`);
      await api("/me/provider-application/acknowledge", { method: "POST" });
    }
  } catch {}
})();
