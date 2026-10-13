import { writeFile, readFile } from "node:fs/promises";
const baseUrl = process.env.RENTTOOL_TEST_URL || "http://127.0.0.1:8088";
const debugUrl = process.env.CHROME_DEBUG_URL || "http://127.0.0.1:9228";
const pages = await (await fetch(debugUrl + "/json/list")).json();
const ws = new WebSocket(
  pages.find((p) => p.type === "page").webSocketDebuggerUrl,
);
await new Promise((resolve, reject) => {
  ws.onopen = resolve;
  ws.onerror = reject;
});
let seq = 0;
const waiting = new Map();
const errors = [];
ws.onmessage = (e) => {
  const m = JSON.parse(e.data);
  if (m.id) {
    const p = waiting.get(m.id);
    waiting.delete(m.id);
    m.error ? p.reject(Error(JSON.stringify(m.error))) : p.resolve(m.result);
  }
  if (m.method === "Runtime.exceptionThrown")
    errors.push(m.params.exceptionDetails.text);
};
const send = (method, params = {}) =>
  new Promise((resolve, reject) => {
    const id = ++seq;
    waiting.set(id, { resolve, reject });
    ws.send(JSON.stringify({ id, method, params }));
  });
const evalJs = async (expression) => {
  const r = await send("Runtime.evaluate", {
    expression,
    returnByValue: true,
    awaitPromise: true,
  });
  if (r.exceptionDetails) throw Error(JSON.stringify(r.exceptionDetails));
  return r.result.value;
};
const delay = (ms) => new Promise((r) => setTimeout(r, ms));
async function until(expression) {
  for (let i = 0; i < 80; i++) {
    if (await evalJs(expression)) return;
    await delay(150);
  }
  throw Error("Timed out: " + expression);
}
async function screenshot(name) {
  const r = await send("Page.captureScreenshot", {
    format: "png",
    captureBeyondViewport: false,
  });
  await writeFile(name, Buffer.from(r.data, "base64"));
}
await send("Runtime.enable");
await send("Page.enable");
await send("Emulation.setDeviceMetricsOverride", {
  width: 1440,
  height: 1000,
  deviceScaleFactor: 1,
  mobile: false,
});
await send("Page.navigate", { url: baseUrl + "/" });
await until('document.querySelectorAll(".product").length===6');
await evalJs("document.fonts.ready");
await screenshot("docs/screenshots/desktop.png");
console.log("PASS: catálogo conectado a MySQL, 6 publicaciones demo.");
await send("Emulation.setDeviceMetricsOverride", {
  width: 390,
  height: 844,
  deviceScaleFactor: 1,
  mobile: true,
});
await delay(300);
const overflow = await evalJs(
  "document.documentElement.scrollWidth>innerWidth",
);
if (overflow) throw Error("Mobile horizontal overflow");
await screenshot("docs/screenshots/mobile.png");
console.log("PASS: móvil sin desbordamiento horizontal.");
await evalJs('document.querySelector(".menu-toggle").click()');
if (!(await evalJs('document.querySelector("nav").classList.contains("open")')))
  throw Error("Mobile menu failed");
await evalJs('document.querySelector(".menu-toggle").click()');
await send("Emulation.setDeviceMetricsOverride", {
  width: 1440,
  height: 1000,
  deviceScaleFactor: 1,
  mobile: false,
});
await evalJs('document.querySelector("[data-auth=register]").click()');
await until('document.querySelector("#auth-dialog").open');
const suffix = Date.now();
const password = crypto.randomUUID() + "A1";
await evalJs(
  `(()=>{const f=document.querySelector('#auth-form');f.elements.name.value='Proveedor de pruebas';f.elements.email.value='browser-${suffix}@test.example';f.elements.password.value=${JSON.stringify(password)};f.elements.role.value='PROVEEDOR';f.requestSubmit();})()`,
);
await until(
  '!document.querySelector("#auth-dialog").open && !document.querySelector("#new-tool").hidden',
);
console.log("PASS: registro JWT y panel del proveedor.");
await send("Emulation.setDeviceMetricsOverride", {width:390,height:844,deviceScaleFactor:1,mobile:true});
if(await evalJs("document.documentElement.scrollWidth>innerWidth"))throw Error("Logged-in mobile horizontal overflow");
if(!await evalJs('getComputedStyle(document.querySelector("#logout")).display!=="none"'))throw Error("Mobile logout hidden");
console.log("PASS: sesión móvil sin desbordamiento y cierre accesible.");
await send("Emulation.setDeviceMetricsOverride", {width:1440,height:1000,deviceScaleFactor:1,mobile:false});
await evalJs('document.querySelector("#new-tool").click()');
await until('document.querySelector("#tool-dialog").open');
await evalJs(
  `(()=>{const f=document.querySelector('#tool-form');f.elements.name.value='Herramienta QA ${suffix}';f.elements.description.value='Publicación automática de verificación';f.elements.categoryId.value='1';f.elements.dailyRate.value='28000';f.requestSubmit();})()`,
);
await until(
  'document.querySelector("[data-manage]") && !document.querySelector("#tool-dialog").open',
);
console.log("PASS: publicar herramienta desde formulario.");
await evalJs('document.querySelector("[data-edit]").click()');
await until('document.querySelector("#tool-dialog").open');
await evalJs(
  'document.querySelector("#tool-form").elements.dailyRate.value="29000";document.querySelector("#tool-form").requestSubmit()',
);
await until(
  '!document.querySelector("#tool-dialog").open && document.querySelector("#inventory").textContent.includes("29.000")',
);
console.log("PASS: editar tarifa y recargar inventario.");
await evalJs('document.querySelector("[data-manage]").click()');
await until('document.querySelector("#manage-dialog").open');
await evalJs(
  'document.querySelector("#unit-form").elements.code.value="QA-001";document.querySelector("#unit-form").requestSubmit()',
);
await until('document.querySelector("#unit-list select")');
console.log("PASS: añadir unidad física.");
await send("DOM.enable");
const doc = await send("DOM.getDocument");
const node = await send("DOM.querySelector", {
  nodeId: doc.root.nodeId,
  selector: "#upload-form input[type=file]",
});
await send("DOM.setFileInputFiles", {
  nodeId: node.nodeId,
  files: [process.cwd() + "/frontend/assets/hero-architecture.png"],
});
await evalJs('document.querySelector("#upload-form").requestSubmit()');
await until('document.querySelector("#image-list img")');
console.log("PASS: subir imagen PNG validada y persistida.");
await evalJs('document.querySelector("#manage-dialog .close").click()');
await evalJs('document.querySelector("#workspace").scrollIntoView()');
await screenshot("docs/screenshots/provider.png");
await evalJs(
  'window.confirm=()=>true;document.querySelector("[data-retire]").click()',
);
await until('!document.querySelector("[data-retire]")');
console.log("PASS: retirada lógica desde interfaz.");
await evalJs('document.querySelector("#logout").click()');
await until('document.querySelector("#workspace").hidden');
if (await evalJs('sessionStorage.getItem("renttool.token")!==null'))
  throw Error("Logout token not cleared");
console.log("PASS: cierre de sesión.");
if (errors.length) throw Error("Browser exceptions: " + JSON.stringify(errors));
console.log("PASS: sin excepciones JavaScript.");
ws.close();
