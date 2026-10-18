import assert from "node:assert/strict";
import {readFile} from "node:fs/promises";
import vm from "node:vm";

const source = await readFile(new URL("../frontend/app.js", import.meta.url), "utf8");
const nodes = new Map();
const node = id => {
  if (!nodes.has(id)) nodes.set(id, {value:"", hidden:false, disabled:false, innerHTML:"", textContent:""});
  return nodes.get(id);
};
node("#sort-order").value = "RECENT";
node("#available-start").value = "2026-11-01";
node("#available-end").value = "2026-11-03";
node("#available-quantity").value = "2";
const pending = [];
const context = vm.createContext({
  $:node, state:{page:0}, URLSearchParams,
  validateAvailabilityFilters:()=>true,
  wireProducts:()=>{}, productCard:t=>t.name,
  EMPTY_CATALOG:"VACÍO",
  catalogError:(_,e)=>{throw e;},
  api:path=>new Promise((resolve,reject)=>pending.push({path,resolve,reject}))
});
const start=source.indexOf("let catalogTicket = 0;");
const end=source.indexOf("async function detail(",start);
vm.runInContext(source.slice(start,end),context);
const first=vm.runInContext("loadTools()",context);
node("#search").value="reciente";
const second=vm.runInContext("loadTools()",context);
assert.match(pending[1].path,/start=2026-11-01/);
assert.match(pending[1].path,/quantity=2/);
pending[1].resolve({content:[{name:"Resultado reciente"}],totalPages:1,totalElements:1});
await second;
pending[0].resolve({content:[{name:"Resultado antiguo"}],totalPages:5,totalElements:50});
await first;
assert.equal(node("#products").innerHTML,"Resultado reciente");
assert.equal(context.state.totalPages,1);
const old=vm.runInContext("loadTools()",context);
const latest=vm.runInContext("loadTools()",context);
pending[3].resolve({content:[{name:"Último"}],totalPages:1,totalElements:1});
await latest;
pending[2].reject(Error("Error de consulta antigua"));
await old;
assert.equal(node("#products").innerHTML,"Último");
console.log("PASS: filtros enviados, respuestas y errores anteriores no reemplazan el catálogo reciente.");
