import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import vm from "node:vm";

const source = await readFile(new URL("../frontend/common.js", import.meta.url), "utf8");
let current;
let restored = 0;
const context = vm.createContext({
  esc: (value) => String(value).replaceAll("<", "&lt;").replaceAll(">", "&gt;"),
  document: {
    activeElement: { isConnected: true, focus: () => restored++ },
    body: { append: (dialog) => { current = dialog; } },
    createElement: () => {
      const nodes = {
        "#confirmation-message": {},
        "[data-cancel]": {},
        'button[type="submit"]': {},
        form: { elements: { reason: { value: "" } } },
      };
      const listeners = {};
      return {
        classList: { contains: () => false },
        setAttribute: () => {},
        querySelector: (selector) => nodes[selector],
        addEventListener: (event, listener) => { listeners[event] = listener; },
        showModal: () => {},
        close: () => { listeners.close(); },
        remove: () => {},
      };
    },
  },
});
vm.runInContext(source.slice(source.indexOf("let confirmationOpen ="),
  source.indexOf("// Marca un campo con error:")), context);
const options = {
  title: "¿Eliminar <imagen>?", message: "<Texto que debe mostrarse literalmente>",
  confirmLabel: "Eliminar", danger: true,
};
let mutations = 0;
const perform = async () => { if (await context.confirmAction(options)) mutations++; };

let pending = perform();
assert.match(current.innerHTML, /&lt;imagen&gt;/);
assert.equal(current.querySelector("#confirmation-message").textContent, options.message);
assert.match(current.innerHTML, /data-cancel autofocus/);
assert.equal(mutations, 0);
assert.equal(await context.confirmAction(options), null, "No debe abrir otra confirmación");
current.querySelector("[data-cancel]").onclick();
await pending;
assert.equal(mutations, 0, "Cancelar no ejecuta la acción");

pending = perform();
current.close(); // Escape/cierre nativo produce el mismo evento close.
await pending;
assert.equal(mutations, 0, "Cerrar no ejecuta la acción");

pending = perform();
const form = current.querySelector("form");
const submit = form.onsubmit;
submit({ preventDefault() {}, target: form });
submit({ preventDefault() {}, target: form });
await pending;
assert.equal(mutations, 1, "Confirmar dos veces solo ejecuta una acción");

pending = context.confirmAction({ ...options, reason: true });
const reasonForm = current.querySelector("form");
reasonForm.elements.reason.value = "  Equipo en revisión  ";
reasonForm.onsubmit({ preventDefault() {}, target: reasonForm });
assert.equal((await pending).reason, "Equipo en revisión");
assert.equal(restored, 4, "Se restaura el foco al cerrar cada diálogo");
console.log("PASS: cancelar, cerrar, confirmar una vez, motivo, foco y contenido seguro.");
