"use strict";
// Rendimiento de proveedores (/admin): ventas comparadas con el promedio y motivos para citarlos.
let performanceVersion = 0;
const performanceForm = $("#performance-form");
performanceForm.elements.from.value = addDays(businessToday(), -29);
performanceForm.elements.to.value = businessToday();
performanceForm.onsubmit = (e) => {
  e.preventDefault();
  loadPerformance();
};
const percent = (v) => (v == null ? "—" : Math.round(v * 100) + " %");

async function loadPerformance() {
  const form = performanceForm, output = $("[data-performance]");
  const error = form.querySelector(".form-error"), button = form.querySelector("button");
  if (!form.reportValidity()) return;
  const current = ++performanceVersion;
  button.disabled = true;
  error.textContent = "";
  output.textContent = "Consultando rendimiento…";
  try {
    const params = new URLSearchParams({ from: form.elements.from.value, to: form.elements.to.value });
    const r = await api("/admin/provider-performance?" + params);
    if (current !== performanceVersion) return;
    renderPerformance(output, r);
  } catch (e) {
    if (current !== performanceVersion) return;
    output.textContent = "";
    showFormError(form, e, error);
  } finally {
    if (current === performanceVersion) button.disabled = false;
  }
}

function renderPerformance(output, r) {
  const cards = [
    [money(r.totalSales), "Ventas totales (COP)"],
    [money(r.averages.salesPerProvider), "Promedio de ventas por proveedor"],
    [percent(r.averages.acceptanceRate), "Aceptación promedio de la plataforma"],
    [r.needAttention, r.needAttention === 1 ? "Proveedor para citar" : "Proveedores para citar"],
  ];
  output.innerHTML =
    '<div class="statistics-cards">' +
    cards.map(([v, l]) => `<div class="stat"><strong>${esc(v)}</strong><span>${l}</span></div>`).join("") +
    "</div>" +
    (r.providers.length
      ? salesChart(r) + performanceTable(r)
      : '<div class="empty"><h3>Aún no hay proveedores.</h3></div>');
  const only = output.querySelector("[data-only-alerts]");
  if (only) only.onchange = () =>
    output.querySelectorAll("tbody tr[data-ok]").forEach((tr) => (tr.hidden = only.checked));
  bindChartTooltip(output);
}

// Barras horizontales: una sola serie (ventas) y una línea de referencia con el promedio.
function salesChart(r) {
  const avg = Number(r.averages.salesPerProvider);
  const max = Math.max(avg, ...r.providers.map((p) => Number(p.sales)));
  if (max <= 0)
    return '<p class="muted performance-empty">Ningún proveedor tiene reservas aceptadas en estas fechas.</p>';
  const pos = (v) => (Number(v) / max) * 100;
  const rows = r.providers.map((p) => {
    const tip = `${p.name}${p.active ? "" : " (inactivo)"} — Ventas: ${money(p.sales)} · ` +
      `${p.requests} solicitudes · ${p.accepted} aceptadas · Aceptación: ${percent(p.acceptanceRate)}`;
    return `<div class="bar-row" tabindex="0" data-tip="${esc(tip)}">` +
      `<span class="bar-label">${p.alerts.length ? '<span class="bar-alert" aria-label="Requiere atención">⚠</span> ' : ""}${esc(p.name)}${p.active ? "" : ' <small class="muted">(inactivo)</small>'}</span>` +
      `<span class="bar-track"><span class="bar${p.active ? "" : " bar-inactive"}" style="width:${pos(p.sales)}%"></span>` +
      `<span class="bar-value" style="left:${pos(p.sales)}%">${esc(money(p.sales))}</span></span></div>`;
  }).join("");
  return `<figure class="sales-chart" aria-label="Ventas por proveedor comparadas con el promedio">` +
    `<figcaption><strong>Ventas por proveedor (COP)</strong><span class="muted">La línea marca el promedio de los proveedores activos: ${esc(money(avg))}</span></figcaption>` +
    `<div class="bar-rows" style="--avg:${pos(avg) / 100}"><span class="avg-line" aria-hidden="true"><span>Promedio</span></span>${rows}</div>` +
    `<div class="chart-tooltip" role="tooltip" hidden></div></figure>`;
}

function performanceTable(r) {
  const rows = r.providers.map((p) => {
    const contact = [p.email, p.phone, p.city].filter(Boolean).map(esc).join(" · ");
    const alerts = p.alerts.length
      ? p.alerts.map((a) => `<span class="status-chip status-warning">⚠ ${esc(a)}</span>`).join(" ")
      : p.active ? '<span class="status-chip status-aceptada">✓ Sin alertas</span>' : '<span class="status-chip">Inactivo</span>';
    return `<tr${p.alerts.length ? "" : " data-ok"}><th scope="row">${esc(p.name)}<small class="muted">${contact}</small></th>` +
      `<td class="alerts-cell">${alerts}</td><td>${p.activeTools}</td><td>${p.requests}</td><td>${p.accepted}</td><td>${p.rejected}</td>` +
      `<td>${p.expired}</td><td>${p.pending}</td><td>${percent(p.acceptanceRate)}</td>` +
      `<td>${esc(money(p.sales))}</td><td>${esc(money(p.averageTicket))}</td><td>${p.unitsRented}</td></tr>`;
  }).join("");
  const a = r.averages;
  return `<div class="performance-table-head"><h4>Detalle por proveedor</h4>` +
    `<label class="check-label"><input type="checkbox" data-only-alerts> Solo proveedores para citar</label></div>` +
    `<div class="table-scroll"><table class="performance-table"><thead><tr><th scope="col">Proveedor y contacto</th>` +
    `<th scope="col">Alertas</th><th scope="col">Herramientas activas</th><th scope="col">Solicitudes</th><th scope="col">Aceptadas</th>` +
    `<th scope="col">Rechazadas</th><th scope="col">Vencidas sin respuesta</th><th scope="col">Pendientes</th>` +
    `<th scope="col">Aceptación</th><th scope="col">Ventas</th><th scope="col">Venta promedio</th>` +
    `<th scope="col">Unidades alquiladas</th></tr></thead><tbody>${rows}</tbody>` +
    `<tfoot><tr><th scope="row">Promedio de la plataforma</th><td></td><td></td><td>${a.requestsPerProvider.toFixed(1)}</td>` +
    `<td></td><td></td><td>${percent(a.expiredRate)}</td><td></td><td>${percent(a.acceptanceRate)}</td>` +
    `<td>${esc(money(a.salesPerProvider))}</td><td></td><td></td></tr></tfoot></table></div>` +
    `<p class="muted performance-note">Alertas: ventas por debajo de la mitad del promedio, 25 % o más de solicitudes vencidas sin responder, ` +
    `aceptación menor al 50 % con al menos 3 solicitudes respondidas, o sin herramientas publicadas o sin solicitudes en el período.</p>`;
}

function bindChartTooltip(root) {
  const tooltip = root.querySelector(".chart-tooltip");
  if (!tooltip) return;
  const chart = tooltip.parentElement;
  const show = (row) => {
    tooltip.textContent = row.dataset.tip;
    tooltip.hidden = false;
    const box = chart.getBoundingClientRect(), target = row.getBoundingClientRect();
    tooltip.style.top = target.bottom - box.top + 6 + "px";
  };
  root.querySelectorAll(".bar-row").forEach((row) => {
    row.addEventListener("mouseenter", () => show(row));
    row.addEventListener("focus", () => show(row));
    row.addEventListener("mouseleave", () => (tooltip.hidden = true));
    row.addEventListener("blur", () => (tooltip.hidden = true));
  });
}
