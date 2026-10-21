"use strict";
(() => {
  let version = 0;
  const panel = document.querySelector("#statistics-panel");
  if (!panel) return;
  const form = panel.querySelector("form"), output = panel.querySelector("[data-statistics]");
  const error = panel.querySelector(".form-error"), button = form.querySelector("button");
  form.elements.from.value = addDays(businessToday(), -29);
  form.elements.to.value = businessToday();
  const labels = {SOLICITADA:"Solicitadas", ACEPTADA:"Aceptadas", RECHAZADA:"Rechazadas", CANCELADA:"Canceladas"};
  async function refresh() {
    if (!state.user || state.user.role !== "PROVEEDOR") return;
    if (!form.reportValidity()) return;
    const current = ++version;
    button.disabled = true;
    error.textContent = "";
    output.textContent = "Consultando estadísticas…";
    try {
      const params = new URLSearchParams({from: form.elements.from.value, to: form.elements.to.value});
      const s = await api("/statistics?" + params);
      if (current !== version) return;
      const items = [
        [s.reservations,"Reservas solicitadas"],
        [money(s.sales),"Ventas"], [money(s.paidAwaitingAcceptance),"Pagadas por aceptar"],
        [s.inUse,"Entregadas / en uso"],
        [s.openIncidents,"Incidencias abiertas"]
      ];
      output.innerHTML = '<div class="statistics-cards">' +
        items.map(([value,label]) => '<div class="stat"><strong>' + esc(value) + '</strong><span>' + label + '</span></div>').join("") +
        '</div><h3>Estado actual de las reservas del período</h3><ul class="statistics-status">' +
        Object.entries(labels).map(([key,label]) => '<li>' + label + ': <strong>' + (s.byStatus[key] ?? 0) + '</strong></li>').join("") +
        '</ul><h3>Herramientas más solicitadas</h3>' + (s.popularTools.length ?
          '<ol>' + s.popularTools.map(t => '<li>' + esc(t.name) + ': ' + t.reservations +
            ' solicitudes · ' + t.units + ' unidades solicitadas</li>').join("") + '</ol>' :
          '<p>No hay reservas solicitadas en estas fechas.</p>');
    } catch (e) {
      if (current !== version) return;
      output.textContent = "";
      showFormError(form, e, error);
    } finally {
      if (current === version) button.disabled = false;
    }
  }
  form.onsubmit = e => {e.preventDefault(); refresh();};
  panel.addEventListener("toggle", () => { if (panel.open) refresh(); });
  document.addEventListener("reservations:loaded", () => { if (panel.open) refresh(); });
})();
