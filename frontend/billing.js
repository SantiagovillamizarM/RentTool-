"use strict";

// Pago de demostración: todo ocurre en RentTool y no se solicita información bancaria.
document.body.insertAdjacentHTML("beforeend",
  '<dialog id="billing-dialog" aria-labelledby="billing-title">' +
  '<button class="close" aria-label="Cerrar">×</button>' +
  '<h2 id="billing-title">Pagos y factura</h2>' +
  '<p class="badge">Simulación escolar: no se cobra dinero real.</p>' +
  '<div id="billing-content"></div>' +
  '<p id="billing-error" class="form-error" role="alert"></p></dialog>');
$("#billing-dialog .close").onclick = () => $("#billing-dialog").close();

async function openBilling(id) {
  const dialog = $("#billing-dialog");
  const box = $("#billing-content");
  const error = $("#billing-error");
  error.textContent = "";
  box.textContent = "Consultando pagos…";
  if (!dialog.open) dialog.showModal();

  try {
    const summary = await api("/reservations/" + id + "/payments");
    const labels = {
      APPROVED: "Aprobado (simulado)", DECLINED: "Rechazado (simulado)",
      PENDING: "Pendiente", ERROR: "Error", VOIDED: "Anulado", REVIEW: "Por revisar",
    };
    box.innerHTML = "<p>Reserva #" + esc(id) + "</p>" +
      (summary.payments.length
        ? summary.payments.map(payment =>
          "<p><strong>" + esc(labels[payment.status] || payment.status) +
          "</strong> · " + money(payment.amount) +
          "<br><small>" + esc(payment.reference) + "</small></p>").join("")
        : "<p>Aún no hay pagos registrados.</p>") +
      (summary.payable
        ? '<form id="demo-payment-form"><label>Resultado de la simulación' +
          '<select name="result"><option value="APROBADO">Pago aprobado</option>' +
          '<option value="RECHAZADO">Pago rechazado</option></select></label>' +
          '<button class="button">Simular pago</button></form>'
        : "") +
      (summary.invoiceNumber
        ? '<button type="button" class="button" data-invoice>Descargar factura de demostración</button>'
        : '<p class="muted">La factura se genera automáticamente al aprobar el pago simulado de una reserva aceptada.</p>');

    const form = box.querySelector("#demo-payment-form");
    if (form) form.onsubmit = async event => {
      event.preventDefault();
      const result = form.elements.result.value;
      const confirmed = await confirmAction({
        title: "¿Registrar este pago de demostración?",
        message: result === "APROBADO"
          ? "Se registrará un pago simulado aprobado y se generará la factura. No se cobra dinero real."
          : "Se registrará un intento rechazado. Podrás volver a intentar el pago.",
        confirmLabel: "Registrar simulación",
      });
      if (!confirmed) return;
      const button = form.querySelector("button");
      button.disabled = true;
      try {
        await api("/reservations/" + id + "/payments", {
          method: "POST", body: JSON.stringify({ result }),
        });
        await openBilling(id);
      } catch (failure) {
        error.textContent = failure.message;
      } finally {
        button.disabled = false;
      }
    };

    const download = box.querySelector("[data-invoice]");
    if (download) download.onclick = async () => {
      download.disabled = true;
      try {
        const response = await fetch("/api/v1/reservations/" + id + "/invoice", {
          headers: { Authorization: "Bearer " + state.token },
        });
        if (!response.ok) throw new Error("No se pudo descargar la factura. Inicia sesión de nuevo e inténtalo.");
        const url = URL.createObjectURL(await response.blob());
        const link = document.createElement("a");
        link.href = url;
        link.download = summary.invoiceNumber + ".html";
        link.click();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
      } catch (failure) {
        error.textContent = failure.message;
      } finally {
        download.disabled = false;
      }
    };
  } catch (failure) {
    box.textContent = "";
    error.textContent = failure.message;
  }
}
