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
          if (
            !confirm(
              `¿Retirar "${b.dataset.name}" del catálogo? Dejará de verse en el sitio; su registro se conserva.`,
            )
          )
            return;
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
    onSaved: (tool, created) => {
      loadInventory();
      if (created) {
        notify("Herramienta publicada. Agrega sus unidades para que se pueda reservar.", SEE_CATALOG);
        manageTool(tool.id);
      }
    },
  });

// Unidades físicas e imágenes adicionales de una herramienta.
async function manageTool(id) {
  state.managing = id;
  $("#manage-error").textContent = "";
  try {
    const [t, units] = await Promise.all([api("/tools/" + id), api("/tools/" + id + "/units")]);
    $("#manage-title").textContent = t.name;
    $("#unit-list").innerHTML = units.length
      ? units
          .map(
            (u) =>
              `<div class="manage-row"><span>${esc(u.code)}</span><select aria-label="Estado de ${esc(u.code)}" data-unit="${u.id}" data-code="${esc(u.code)}">${["OPERATIVA", "MANTENIMIENTO", "RETIRADA"].map((s) => `<option ${s === u.status ? "selected" : ""}>${s}</option>`).join("")}</select></div>`,
          )
          .join("")
      : '<p class="muted">Sin unidades registradas. Agrega al menos una operativa.</p>';
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
            } catch (e) {
              $("#manage-error").textContent = e.message;
              await manageTool(id);
            }
          }),
      );
    $("#image-list").innerHTML = t.images.length
      ? t.images
          .map(
            (i, n) =>
              `<div class="manage-row"><img src="${esc(i.location)}" alt="Imagen ${n + 1}" referrerpolicy="no-referrer"><span>${n === 0 ? '<span class="status-chip status-aceptada">Principal</span>' : `Imagen ${n + 1}`}</span><button data-remove-image="${i.id}">Eliminar ×</button></div>`,
          )
          .join("")
      : '<p class="muted">Sin imágenes: el catálogo muestra la ilustración predeterminada.</p>';
    $("#image-list")
      .querySelectorAll("button")
      .forEach(
        (b) =>
          (b.onclick = async () => {
            try {
              await api(`/tools/${id}/images/${b.dataset.removeImage}`, { method: "DELETE" });
              await manageTool(id);
              loadInventory();
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
      $("#manage-error").textContent = error.message;
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
    await manageTool(state.managing);
    loadInventory();
    notify("Imagen subida.");
  } catch (error) {
    $("#manage-error").textContent = error.message;
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
  $("#stat-active").textContent = reservations.filter((r) => r.status === "ACEPTADA").length;
  $("#tab-pending").hidden = pending === 0;
  $("#tab-pending").textContent = pending;
});

// Mi empresa: datos legales de solo lectura.
async function loadCompany() {
  const box = $("#company-data");
  try {
    const p = await api("/me/provider-profile");
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
