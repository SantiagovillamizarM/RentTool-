"use strict";
// Editor de herramientas compartido por el panel del administrador y el del proveedor: datos,
// tarifa e imagen principal. Sin id crea una herramienta nueva (solo el proveedor publica).
const MAX_IMAGE_BYTES = 5 * 1024 * 1024;
const toolEditor = {
  id: null,
  mainImage: null,
  preview: null,
  imageAction: null,
  onSaved: null,
  categories: null,
};
document.body.insertAdjacentHTML(
  "beforeend",
  `<dialog id="tool-edit-dialog">
      <button class="close" aria-label="Cerrar">×</button>
      <p class="eyebrow" id="tool-edit-eyebrow">EDITAR HERRAMIENTA</p>
      <h2 id="tool-edit-title">Actualiza la herramienta.</h2>
      <p class="muted" id="tool-edit-owner"></p>
      <form id="tool-edit-form" novalidate>
        <div class="logo-picker">
          <span class="tool-preview" id="tool-preview"></span>
          <div>
            <p class="logo-title">Imagen principal</p>
            <p class="muted">PNG o JPEG hasta 5 MB. Sin imagen se muestra la ilustración predeterminada.</p>
            <label class="logo-field"><span class="outline-button">Cambiar imagen</span><input name="image" type="file" accept="image/png,image/jpeg" class="visually-hidden" /></label><button type="button" class="text-button" id="tool-image-remove">Quitar imagen</button>
          </div>
        </div>
        <label>Nombre<input name="name" required maxlength="160" /></label
        ><label>Categoría<select name="categoryId" required></select></label
        ><label>Descripción<textarea name="description" required maxlength="3000" rows="3"></textarea></label
        ><label>Tarifa diaria · COP<input name="dailyRate" type="number" min="1" max="999999999999.99" step="0.01" required /></label>
        <p class="muted price-note" id="tool-price-note">Las reservas ya solicitadas conservan la tarifa con que se pidieron; el precio nuevo aplica a las siguientes.</p>
        <p class="form-error" id="tool-edit-error" role="alert"></p>
        <button class="button full" id="tool-edit-submit">Guardar cambios →</button>
      </form>
    </dialog>`,
);
async function categoryOptions() {
  toolEditor.categories ??= await api("/categories");
  return toolEditor.categories
    .map((c) => `<option value="${c.id}">${esc(c.name)}</option>`)
    .join("");
}
function showToolPreview(url) {
  const box = $("#tool-preview");
  box.innerHTML = url
    ? `<img src="${esc(url)}" alt="Imagen principal" referrerpolicy="no-referrer">`
    : toolArt("edit");
  wireImages(box);
  $("#tool-image-remove").hidden = !url;
}
/**
 * Abre el editor. options.owner(tool) devuelve el texto bajo el título; options.onSaved(tool,
 * created) se llama al guardar.
 */
async function openToolEditor(id, button, options = {}) {
  if (button) button.disabled = true;
  try {
    const [t, categories] = await Promise.all([
      id ? api("/tools/" + id) : null,
      categoryOptions(),
    ]);
    const form = $("#tool-edit-form");
    form.reset();
    clearFieldErrors(form);
    $("#tool-edit-error").textContent = "";
    form.elements.categoryId.innerHTML = categories;
    if (t)
      for (const field of ["name", "categoryId", "description", "dailyRate"])
        form.elements[field].value = t[field];
    if (toolEditor.preview) URL.revokeObjectURL(toolEditor.preview);
    Object.assign(toolEditor, {
      id: t?.id ?? null,
      mainImage: t?.images[0] || null,
      preview: null,
      imageAction: null,
      onSaved: options.onSaved || null,
    });
    showToolPreview(t?.images[0]?.location);
    $("#tool-edit-eyebrow").textContent = t ? "EDITAR HERRAMIENTA" : "NUEVA HERRAMIENTA";
    $("#tool-edit-title").textContent = t ? t.name : "Publica tu equipo.";
    $("#tool-edit-owner").textContent = t && options.owner ? options.owner(t) : "";
    $("#tool-edit-owner").hidden = !$("#tool-edit-owner").textContent;
    $("#tool-price-note").hidden = !t;
    $("#tool-edit-submit").textContent = t ? "Guardar cambios →" : "Publicar herramienta →";
    $("#tool-edit-dialog").showModal();
  } catch (e) {
    notify(e.message);
  } finally {
    if (button) button.disabled = false;
  }
}
$("#tool-edit-form").elements.image.addEventListener("change", (e) => {
  const file = e.target.files[0];
  if (!file) return;
  const form = $("#tool-edit-form");
  if (!["image/png", "image/jpeg"].includes(file.type) || file.size > MAX_IMAGE_BYTES) {
    e.target.value = "";
    fieldError(form, "image", "La imagen debe ser PNG o JPEG de hasta 5 MB.");
    return;
  }
  if (toolEditor.preview) URL.revokeObjectURL(toolEditor.preview);
  toolEditor.preview = URL.createObjectURL(file);
  toolEditor.imageAction = "upload";
  showToolPreview(toolEditor.preview);
});
$("#tool-image-remove").onclick = () => {
  $("#tool-edit-form").elements.image.value = "";
  if (toolEditor.preview) URL.revokeObjectURL(toolEditor.preview);
  toolEditor.preview = null;
  toolEditor.imageAction = toolEditor.mainImage ? "remove" : null;
  showToolPreview(null);
};
function toolProblem(form) {
  const v = (name) => form.elements[name].value.trim();
  if (!v("name")) return ["name", "Escribe el nombre de la herramienta."];
  if (!v("categoryId")) return ["categoryId", "Elige una categoría."];
  if (!v("description")) return ["description", "Escribe una descripción."];
  const rate = Number(v("dailyRate"));
  if (!v("dailyRate") || !(rate > 0)) return ["dailyRate", "La tarifa debe ser mayor que cero."];
  if (rate > 999999999999.99) return ["dailyRate", "La tarifa es demasiado alta."];
  return null;
}
$("#tool-edit-form").onsubmit = async (e) => {
  e.preventDefault();
  const form = e.target;
  clearFieldErrors(form);
  $("#tool-edit-error").textContent = "";
  const problem = toolProblem(form);
  if (problem) {
    fieldError(form, ...problem);
    return;
  }
  const button = $("#tool-edit-submit");
  button.disabled = true;
  const created = !toolEditor.id;
  try {
    const saved = await api("/tools" + (created ? "" : "/" + toolEditor.id), {
      method: created ? "POST" : "PUT",
      body: JSON.stringify({
        name: form.elements.name.value.trim(),
        categoryId: Number(form.elements.categoryId.value),
        description: form.elements.description.value.trim(),
        dailyRate: Number(form.elements.dailyRate.value),
      }),
    });
    // Ya existe: si algo falla después, se sigue editando la misma herramienta.
    toolEditor.id = saved.id;
    if (toolEditor.imageAction === "upload") {
      const body = new FormData();
      body.append("file", form.elements.image.files[0]);
      await api(`/tools/${saved.id}/images/main`, { method: "POST", body });
    } else if (toolEditor.imageAction === "remove") {
      await api(`/tools/${saved.id}/images/${toolEditor.mainImage.id}`, { method: "DELETE" });
    }
    $("#tool-edit-dialog").close();
    toolEditor.onSaved?.(saved, created);
  } catch (error) {
    const field = /imagen/i.test(error.message) ? "image" : errorField(error.message);
    if (!(field && form.elements[field] && fieldError(form, field, error.message))) {
      $("#tool-edit-error").textContent = error.message;
      reveal($("#tool-edit-error"), form.closest("dialog"));
    }
  } finally {
    button.disabled = false;
  }
};
