"use strict";
const rentalStateLabel = {
  PENDIENTE_ENTREGA: "Pendiente de entrega", EN_USO: "Entregada / en uso",
  DEVUELTA: "Devuelta", NO_APLICA: "Sin movimiento físico", ENTREGA_BLOQUEADA: "Entrega bloqueada",
};
const incidentTypeLabel = { DANO: "Daño", PERDIDA: "Pérdida", OTRO: "Otra incidencia" };
const returnConditionLabel = { BUENO: "Buen estado", DANADO: "Dañada", PERDIDO: "Perdida" };

document.body.insertAdjacentHTML("beforeend",
  '<dialog id="operations-dialog" aria-labelledby="operations-title">' +
  '<button class="close" aria-label="Cerrar">×</button>' +
  '<h2 id="operations-title">Entrega y devolución</h2>' +
  '<div id="operations-content"></div><p id="operations-error" class="form-error" role="alert"></p></dialog>' +
  '<dialog id="incidents-dialog" aria-labelledby="incidents-title">' +
  '<button class="close" aria-label="Cerrar">×</button>' +
  '<h2 id="incidents-title">Incidencias abiertas</h2><div id="incidents-content"></div></dialog>');
$("#operations-dialog .close").onclick = () => $("#operations-dialog").close();
$("#incidents-dialog .close").onclick = () => $("#incidents-dialog").close();
let operationsRequest = 0;

function incidentMarkup(incident, summary) {
  const unit = summary.units.find(item => item.id === incident.unitId);
  return '<article class="incident-card"><h4>#' + incident.id + " · " +
    esc(incidentTypeLabel[incident.type]) + " · " + (incident.resolvedAt ? "Resuelta" : "Abierta") +
    '</h4><p class="muted">' + (unit ? "Unidad " + esc(unit.code) + " · " : "") +
    "Reportada: " + dateTime(incident.reportedAt) + " · Usuario #" + incident.reportedBy +
    '</p><p class="preserve-lines">' + esc(incident.description) + "</p>" +
    (incident.resolvedAt
      ? '<p class="muted">Resuelta: ' + dateTime(incident.resolvedAt) + " · Usuario #" +
        incident.resolvedBy + '</p><p class="preserve-lines">' + esc(incident.resolution) + "</p>"
      : summary.canManage
        ? '<details><summary>Resolver incidencia</summary><form data-resolve="' + incident.id +
          '"><label>Cómo se resolvió<textarea name="resolution" required maxlength="1000" rows="3"></textarea></label>' +
          (incident.unitId
            ? '<label class="operation-check"><input type="checkbox" name="readyToUse">Equipo revisado y listo para volver a usarse</label>' +
              '<p class="muted">Sin esta opción se conserva su estado de mantenimiento o retirada. Debe haberse devuelto para habilitarlo.</p>'
            : "") +
          '<button class="outline-button">Guardar resolución</button></form></details>'
        : "") + "</article>";
}

// reportOpen: abre directamente el formulario para reportar un problema.
async function openOperations(id, reportOpen = false) {
  const request = ++operationsRequest;
  const dialog = $("#operations-dialog");
  const box = $("#operations-content");
  const error = $("#operations-error");
  $("#operations-title").textContent = "Entrega y devolución · Reserva #" + id;
  box.textContent = "Consultando registros…";
  error.textContent = "";
  if (!dialog.open) dialog.showModal();
  try {
    const summary = await api("/reservations/" + id + "/operations");
    if (request !== operationsRequest) return;
    const operation = summary.operation;
    box.innerHTML = '<p class="status-chip">' + esc(rentalStateLabel[summary.status]) + "</p>" +
      (operation
        ? '<h3>Entrega registrada</h3><p class="muted">' + dateTime(operation.deliveredAt) +
          " · Usuario #" + operation.deliveredBy + '</p><p class="preserve-lines">' +
          esc(operation.deliveryNotes || "Sin observaciones") + "</p>" +
          (operation.returnedAt
            ? '<h3>Devolución registrada</h3><p class="muted">' + dateTime(operation.returnedAt) +
              " · Usuario #" + operation.returnedBy + '</p><p class="preserve-lines">' +
              esc(operation.returnNotes || "Sin observaciones") + "</p>"
            : "")
        : '<p class="muted">' + ({
            PENDIENTE_ENTREGA: "La entrega se registrará automáticamente el día de inicio.",
            ENTREGA_BLOQUEADA: "El alquiler empezó, pero ninguna unidad estaba lista para entregarse (por ejemplo, por mantenimiento). Se intentará de nuevo automáticamente; si no se resuelve, el proveedor o el administrador pueden cancelarla con reembolso.",
          }[summary.status] || "Esta reserva no tiene entrega.") + "</p>") +
      '<div class="operation-units">' + summary.units.map(unit =>
        '<p><strong>' + esc(unit.code) + '</strong> · ' + esc(unit.status) +
        (unit.returnedCondition ? " · Devuelta: " + esc(returnConditionLabel[unit.returnedCondition]) : "") +
        (unit.notes ? '<br><span class="muted">' + esc(unit.notes) + "</span>" : "") + "</p>").join("") + "</div>" +
      (summary.canManage && summary.status === "EN_USO"
        ? '<h3>Revisión de devolución</h3><form id="return-form">' +
          summary.units.map(unit =>
            '<fieldset><legend>' + esc(unit.code) + '</legend><label>Estado al devolver' +
            '<select name="condition-' + unit.id + '" data-condition="' + unit.id + '">' +
            '<option value="BUENO">Buen estado</option><option value="DANADO">Dañada</option>' +
            '<option value="PERDIDO">Perdida</option></select></label><label>Observaciones de la unidad' +
            '<textarea name="notes-' + unit.id + '" maxlength="500" rows="2"></textarea></label></fieldset>').join("") +
          '<label>Observaciones generales<textarea name="notes" maxlength="500" rows="2"></textarea></label>' +
          '<button class="button">Registrar devolución</button></form>'
        : "") +
      (summary.canReport
        ? '<details class="incident-report"' + (reportOpen ? " open" : "") + '><summary>' +
          (summary.canManage ? "Reportar una incidencia" : "Reportar un problema con el equipo") +
          '</summary><form id="incident-form">' +
          (summary.canManage ? "" : '<p class="muted">Cuéntale al proveedor qué pasó. Lo revisará y te contactará.</p>') +
          '<label>Tipo<select name="type"><option value="OTRO">' + (summary.canManage ? "Otra incidencia" : "Falla o problema de uso") + '</option>' +
          '<option value="DANO">Daño</option><option value="PERDIDA">Pérdida</option></select></label>' +
          '<label>Unidad<select name="unitId"><option value="">Sin unidad específica</option>' +
          summary.units.map(unit => '<option value="' + unit.id + '">' + esc(unit.code) + "</option>").join("") +
          '</select></label><label>Descripción<textarea name="description" required maxlength="1000" rows="3"' +
          (summary.canManage ? "" : ' placeholder="Por ejemplo: el taladro se apaga solo después de unos minutos"') + '></textarea></label>' +
          '<button class="outline-button">' + (summary.canManage ? "Registrar incidencia" : "Enviar reporte") + '</button></form></details>'
        : "") +
      '<h3>Incidencias e historial de revisión</h3>' +
      (summary.incidents.length ? summary.incidents.map(item => incidentMarkup(item, summary)).join("")
        : '<p class="muted">Sin incidencias registradas.</p>');

    const submitAction = async (form, path, body, title, message) => {
      const button = form.querySelector("button");
      if (button.disabled || !form.reportValidity()) return;
      if (!await confirmAction({ title, message, confirmLabel: "Confirmar registro" })) return;
      button.disabled = true;
      error.textContent = "";
      try {
        await api(path, { method: "POST", body: JSON.stringify(body) });
        await openOperations(id);
        loadReservations();
        if (typeof loadInventory === "function") loadInventory();
        if (typeof loadTools === "function") loadTools();
        if (typeof loadCatalog === "function") loadCatalog();
      } catch (failure) {
        showFormError(form, failure, error);
      } finally {
        button.disabled = false;
      }
    };
    const returns = box.querySelector("#return-form");
    if (returns) {
      returns.querySelectorAll("[data-condition]").forEach(select => {
        select.onchange = () => {
          returns.elements["notes-" + select.dataset.condition].required = select.value !== "BUENO";
        };
      });
      returns.onsubmit = event => {
        event.preventDefault();
        const units = summary.units.map(unit => ({
          unitId: unit.id, condition: returns.elements["condition-" + unit.id].value,
          notes: returns.elements["notes-" + unit.id].value,
        }));
        submitAction(returns, "/reservations/" + id + "/return", { notes: returns.elements.notes.value, units },
          "¿Confirmar la devolución de todas las unidades?",
          "Las unidades en buen estado quedarán disponibles. Las dañadas pasarán a mantenimiento y las perdidas quedarán retiradas. La revisión se conservará en el historial.");
      };
    }
    const report = box.querySelector("#incident-form");
    if (report && reportOpen) report.elements.description.focus();
    if (report) {
      report.elements.type.onchange = () => {
        report.elements.unitId.required = report.elements.type.value !== "OTRO";
      };
      report.onsubmit = event => {
        event.preventDefault();
        submitAction(report, "/reservations/" + id + "/incidents", {
          type: report.elements.type.value, unitId: Number(report.elements.unitId.value) || null,
          description: report.elements.description.value,
        }, "¿Registrar la incidencia?", "El reporte quedará en el historial. Un daño o pérdida bloqueará la unidad hasta su revisión.");
      };
    }
    box.querySelectorAll("[data-resolve]").forEach(form => {
      form.onsubmit = event => {
        event.preventDefault();
        submitAction(form, "/incidents/" + form.dataset.resolve + "/resolve", {
          resolution: form.elements.resolution.value, readyToUse: Boolean(form.elements.readyToUse?.checked),
        }, "¿Guardar la resolución?", "Se conservará el reporte y la explicación de su resolución. La unidad solo se habilitará si confirmas que está revisada, devuelta y no tiene otros daños pendientes.");
      };
    });
  } catch (failure) {
    if (request !== operationsRequest) return;
    box.textContent = "";
    error.textContent = failure.message;
  }
}

document.addEventListener("reservations:loaded", () => {
  if (state.user?.role !== "ADMINISTRADOR" || $("#open-incidents")) return;
  const button = document.createElement("button");
  button.id = "open-incidents";
  button.className = "outline-button";
  button.textContent = "Supervisar incidencias abiertas";
  button.onclick = () => openIncidentOverview(0);
  $("#reservations").before(button);
});

async function openIncidentOverview(page) {
  const dialog = $("#incidents-dialog");
  const box = $("#incidents-content");
  if (!dialog.open) dialog.showModal();
  box.textContent = "Consultando incidencias…";
  try {
    const data = await api("/incidents?page=" + page + "&size=20");
    box.innerHTML = data.content.length
      ? data.content.map(incident =>
          '<article class="incident-card"><strong>#' + incident.id + " · " +
          esc(incidentTypeLabel[incident.type]) + '</strong><p class="preserve-lines">' +
          esc(incident.description) + '</p><p class="muted">' + dateTime(incident.reportedAt) +
          '</p><button class="outline-button" data-reservation="' + incident.reservationId +
          '">Ver reserva #' + incident.reservationId + "</button></article>").join("")
      : '<p class="muted">No hay incidencias abiertas.</p>';
    box.innerHTML += '<div class="pagination"><button class="outline-button" data-prev>Anterior</button>' +
      '<span>' + (data.totalPages ? page + 1 : 0) + " / " + data.totalPages + "</span>" +
      '<button class="outline-button" data-next>Siguiente</button></div>';
    box.querySelector("[data-prev]").disabled = page === 0;
    box.querySelector("[data-next]").disabled = page + 1 >= data.totalPages;
    box.querySelector("[data-prev]").onclick = () => openIncidentOverview(page - 1);
    box.querySelector("[data-next]").onclick = () => openIncidentOverview(page + 1);
    box.querySelectorAll("[data-reservation]").forEach(button => {
      button.onclick = () => { dialog.close(); openOperations(button.dataset.reservation); };
    });
  } catch (failure) {
    box.textContent = failure.message;
  }
}
