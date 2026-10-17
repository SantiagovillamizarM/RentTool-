import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import vm from "node:vm";

const source = await readFile(new URL("../frontend/common.js", import.meta.url), "utf8");
const start = source.indexOf("function showFormError(");
const end = source.indexOf("// Las fechas de alquiler", start);
const marked = [];
const context = vm.createContext({
  state: { token: null },
  FormData,
  clearFieldErrors: () => marked.splice(0),
  fieldError: (form, name, message) => {
    if (!form.elements[name]) return false;
    marked.push({ name, message });
    return true;
  },
  errorField: () => null,
  reveal: () => {},
  document: { scrollingElement: {} },
});
vm.runInContext(source.slice(start, end), context);

context.fetch = async () => { throw new TypeError("Failed to fetch"); };
await assert.rejects(context.api("/tools"), /Revisa tu conexión/);

context.fetch = async () => ({ ok: false, status: 503, json: async () => { throw Error(); } });
await assert.rejects(context.api("/tools"), /unos momentos/);

context.fetch = async () => ({
  ok: false, status: 400,
  json: async () => ({ detail: "Corrige los datos", fieldErrors: {
    email: "Correo inválido", "profile.city": "Ciudad obligatoria",
  } }),
});
let error;
try { await context.api("/auth/register"); } catch (e) { error = e; }
assert.equal(error.status, 400);
const form = { elements: { email: { value: "invalid" }, city: { value: "" } }, closest: () => null };
const summary = {};
context.showFormError(form, error, summary);
assert.deepEqual(marked.map((f) => f.name), ["city", "email"]);
assert.equal(form.elements.email.value, "invalid");
assert.equal(summary.textContent, "Corrige los campos señalados.");

context.fetch = async () => ({ ok: true, status: 204 });
assert.equal(await context.api("/tools/1", { method: "DELETE" }), null);
console.log("PASS: conexión, servidor, errores por campo, conservación de datos y respuesta 204.");
