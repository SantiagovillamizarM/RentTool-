"use strict";
// Ingresos y rentabilidad por herramienta o por mes. Sirve al panel del proveedor (sus
// herramientas) y al del administrador (toda la plataforma); el servidor decide qué ve cada uno.
(() => {
  let version = 0;
  const panel = document.querySelector("#revenue-panel");
  if (!panel) return;
  const form = panel.querySelector("form"), output = panel.querySelector("[data-revenue]");
  const error = panel.querySelector(".form-error"), button = form.querySelector("button");
  form.elements.from.value = addDays(businessToday(), -89);
  form.elements.to.value = businessToday();
  async function refresh() {
    if (!state.user || !["PROVEEDOR", "ADMINISTRADOR"].includes(state.user.role)) return;
    if (!form.reportValidity()) return;
    const current = ++version;
    button.disabled = true;
    error.textContent = "";
    output.textContent = "Consultando ingresos…";
    try {
      const params = new URLSearchParams({
        from: form.elements.from.value, to: form.elements.to.value, groupBy: form.elements.groupBy.value,
      });
      const r = await api("/statistics/revenue?" + params);
      if (current !== version) return;
      const byTool = r.groupBy === "TOOL";
      const cards = [
        [money(r.income), "Ingresos totales"], [money(r.rentalIncome), "Por alquileres"],
        [money(r.extraCharges), "Por multas y daños"], [r.reservations, "Reservas vendidas"],
      ];
      output.innerHTML = '<div class="statistics-cards">' +
        cards.map(([value, label]) => '<div class="stat"><strong>' + esc(value) + "</strong><span>" + label + "</span></div>").join("") +
        "</div>" + (r.rows.length
          ? '<div class="table-scroll"><table class="performance-table"><thead><tr><th scope="col">' +
            (byTool ? "Herramienta" : "Mes") + '</th><th scope="col">Reservas</th><th scope="col">Días-unidad</th>' +
            '<th scope="col">Alquiler</th><th scope="col">Multas y daños</th><th scope="col">Ingreso</th>' +
            '<th scope="col">Promedio por reserva</th><th scope="col">Ingreso por día-unidad</th><th scope="col">% del total</th></tr></thead><tbody>' +
            r.rows.map((row) => '<tr><th scope="row">' + esc(row.label) + "</th><td>" + row.reservations +
              "</td><td>" + row.unitDays + "</td><td>" + money(row.rentalIncome) + "</td><td>" +
              money(row.extraCharges) + "</td><td><strong>" + money(row.income) + "</strong></td><td>" +
              money(row.averagePerReservation) + "</td><td>" + money(row.incomePerUnitDay) + "</td><td>" +
              row.share.toFixed(1) + " %</td></tr>").join("") + "</tbody></table></div>"
          : "<p>No hay ventas en estas fechas.</p>");
    } catch (e) {
      if (current !== version) return;
      output.textContent = "";
      showFormError(form, e, error);
    } finally {
      if (current === version) button.disabled = false;
    }
  }
  form.onsubmit = (e) => { e.preventDefault(); refresh(); };
  form.elements.groupBy.onchange = refresh;
  panel.addEventListener("toggle", () => { if (panel.open) refresh(); });
})();
