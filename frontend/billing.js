"use strict";

// Pasarela de pagos simulada: todo ocurre en RentTool y no se mueve dinero real. Los datos de la
// tarjeta solo viajan para validarse; el servidor guarda la franquicia y los últimos 4 dígitos.
document.body.insertAdjacentHTML("beforeend",
  '<dialog id="billing-dialog" aria-labelledby="billing-title">' +
  '<button class="close" aria-label="Cerrar">×</button>' +
  '<h2 id="billing-title">Pagos y factura</h2>' +
  '<div id="billing-content"></div>' +
  '<p id="billing-error" class="form-error" role="alert"></p></dialog>');
$("#billing-dialog .close").onclick = () => $("#billing-dialog").close();

const paymentLabels = {
  APPROVED: "Aprobado", DECLINED: "Rechazado", VOIDED: "Reembolsado",
  PENDING: "Pendiente", ERROR: "Error", REVIEW: "Por revisar",
};
const paymentChip = { APPROVED: "status-aceptada", DECLINED: "status-warning", VOIDED: "" };
const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

function paymentHistory(payments) {
  if (!payments.length) return '<p class="muted">Aún no hay pagos registrados.</p>';
  return '<ul class="payment-history">' + payments.map((p) =>
    `<li><span class="status-chip ${paymentChip[p.status] ?? ""}">${esc(paymentLabels[p.status] || p.status)}</span> ` +
    `<strong>${money(p.amount)}</strong>${p.detail ? " · " + esc(p.detail) : ""}` +
    (p.message ? `<br><span class="muted">${esc(p.message)}</span>` : "") +
    `<br><small class="muted">Ref. ${esc(p.reference)}</small></li>`).join("") + "</ul>";
}

const checkoutForm = (amount) =>
  '<form id="demo-payment-form" class="checkout" novalidate autocomplete="off">' +
  '<fieldset class="pay-methods"><legend>Método de pago</legend>' +
  '<label><input type="radio" name="method" value="TARJETA" checked> Tarjeta crédito o débito</label>' +
  '<label><input type="radio" name="method" value="NEQUI"> Nequi</label></fieldset>' +
  '<div class="pay-fields" data-method="TARJETA">' +
  '<label class="wide">Número de la tarjeta<input name="number" inputmode="numeric" maxlength="23" placeholder="4242 4242 4242 4242" autocomplete="off"></label>' +
  '<label class="wide">Nombre en la tarjeta<input name="holder" maxlength="80" placeholder="ANA RUIZ" autocomplete="off"></label>' +
  '<label>Vence (MM/AA)<input name="exp" inputmode="numeric" maxlength="5" placeholder="12/30" autocomplete="off"></label>' +
  '<label>CVV<input name="cvv" inputmode="numeric" maxlength="4" placeholder="123" autocomplete="off"></label></div>' +
  '<div class="pay-fields" data-method="NEQUI" hidden>' +
  '<label class="wide">Celular registrado en Nequi<input name="phone" type="tel" inputmode="numeric" maxlength="12" placeholder="300 123 4567" autocomplete="off"></label></div>' +
  '<details class="test-data"><summary>Tarjetas aceptadas</summary><ul>' +
  '<li>Visa <code>4242 4242 4242 4242</code></li>' +
  '<li>Mastercard <code>5555 5555 5555 4444</code></li>' +
  '<li>Con cualquier fecha de vencimiento futura y CVV de 3 dígitos.</li>' +
  '<li>Nequi: tu celular registrado.</li></ul></details>' +
  '<p class="pay-status" aria-live="polite"></p>' +
  `<button class="button">Pagar ${money(amount)}</button></form>`;

async function openBilling(id) {
  const dialog = $("#billing-dialog");
  const box = $("#billing-content");
  const error = $("#billing-error");
  error.textContent = "";
  box.textContent = "Consultando pagos…";
  if (!dialog.open) dialog.showModal();

  try {
    const summary = await api("/reservations/" + id + "/payments");
    box.innerHTML = `<p>Reserva #${esc(id)} · Total ${money(summary.amount)}</p>` +
      paymentHistory(summary.payments) +
      (summary.payable ? checkoutForm(summary.amount) : "") +
      (summary.invoiceNumber
        ? '<button type="button" class="button" data-invoice>Descargar factura</button>'
        : summary.payable ? "" : '<p class="muted">La factura se genera al aprobarse el pago.</p>');

    const form = box.querySelector("#demo-payment-form");
    if (form) bindCheckout(form, id, error);
    bindInvoice(box, id, summary.invoiceNumber, error);
  } catch (failure) {
    box.textContent = "";
    error.textContent = failure.message;
  }
}

function bindCheckout(form, id, error) {
  const f = form.elements;
  const method = () => form.querySelector("[name=method]:checked").value;
  form.querySelectorAll("[name=method]").forEach((radio) => radio.onchange = () =>
    form.querySelectorAll(".pay-fields").forEach((box) => (box.hidden = box.dataset.method !== method())));
  // Formato mientras se escribe: grupos de 4 dígitos y MM/AA.
  f.number.oninput = () => (f.number.value = f.number.value.replace(/\D/g, "").slice(0, 19).replace(/(\d{4})(?=\d)/g, "$1 "));
  f.exp.oninput = () => (f.exp.value = f.exp.value.replace(/\D/g, "").slice(0, 4).replace(/^(\d{2})(\d)/, "$1/$2"));
  f.cvv.oninput = () => (f.cvv.value = f.cvv.value.replace(/\D/g, ""));

  form.onsubmit = async (event) => {
    event.preventDefault();
    error.textContent = "";
    const status = form.querySelector(".pay-status");
    const button = form.querySelector("button");
    const card = method() === "TARJETA";
    const [month, year] = f.exp.value.split("/").map(Number);
    const body = card
      ? { method: "TARJETA", number: f.number.value, holder: f.holder.value.trim(), expMonth: month || 0, expYear: year || 0, cvv: f.cvv.value }
      : { method: "NEQUI", phone: f.phone.value };
    button.disabled = true;
    // Simula el tiempo que tarda la pasarela; con Nequi, la espera de la aprobación en la app.
    status.textContent = card ? "Procesando el pago con tu banco…" : "Te enviamos una notificación. Aprueba el pago en la app de Nequi…";
    await wait(card ? 1200 : 2500);
    try {
      const payment = await api("/reservations/" + id + "/payments", { method: "POST", body: JSON.stringify(body) });
      // Los datos de la tarjeta no se quedan en la página después de enviarlos.
      f.number.value = f.cvv.value = f.exp.value = "";
      if (payment.status === "APPROVED") {
        if (typeof loadReservations === "function") loadReservations();
        showReceipt(id, payment);
        return;
      }
      status.textContent = "";
      error.textContent = `Pago rechazado: ${payment.message || "intenta con otro método"}.`;
      await openBillingHistoryOnly(id);
    } catch (failure) {
      status.textContent = "";
      error.textContent = failure.message;
    } finally {
      button.disabled = false;
    }
  };
}

// Tras un rechazo se actualiza el historial sin borrar el formulario ni el mensaje.
async function openBillingHistoryOnly(id) {
  try {
    const summary = await api("/reservations/" + id + "/payments");
    const list = $("#billing-content .payment-history, #billing-content > p.muted");
    if (list) list.outerHTML = paymentHistory(summary.payments);
  } catch { /* el mensaje de rechazo ya está visible */ }
}

function showReceipt(id, payment) {
  const box = $("#billing-content");
  box.innerHTML =
    '<div class="receipt"><div class="receipt-mark" aria-hidden="true">✓</div>' +
    `<h3>Pago aprobado</h3><p><strong>${money(payment.amount)}</strong> · ${esc(payment.detail || "")}</p>` +
    `<p class="muted">Referencia ${esc(payment.reference)}</p>` +
    '<p>El proveedor ya puede aceptar tu reserva. Te llamará para coordinar la entrega. Si la rechaza o no responde a tiempo, el pago se reembolsa.</p>' +
    '<button type="button" class="button" data-invoice>Descargar factura</button></div>';
  bindInvoice(box, id, "RT-" + id, $("#billing-error"));
}

function bindInvoice(box, id, invoiceNumber, error) {
  const download = box.querySelector("[data-invoice]");
  if (!download) return;
  download.onclick = async () => {
    download.disabled = true;
    try {
      const response = await fetch("/api/v1/reservations/" + id + "/invoice", {
        headers: { Authorization: "Bearer " + state.token },
      });
      if (!response.ok) throw new Error("No se pudo descargar la factura. Inicia sesión de nuevo e inténtalo.");
      const url = URL.createObjectURL(await response.blob());
      const link = document.createElement("a");
      link.href = url;
      link.download = invoiceNumber + ".pdf";
      link.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (failure) {
      error.textContent = failure.message;
    } finally {
      download.disabled = false;
    }
  };
}
