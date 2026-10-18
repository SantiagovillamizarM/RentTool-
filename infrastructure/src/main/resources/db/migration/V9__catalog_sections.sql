-- El catálogo pasa a tres secciones: Eléctricas, Iluminación y Manuales.
-- Las herramientas existentes se reasignan antes de quitar la categoría que sobra:
-- construcción y medición eran equipos eléctricos; trabajo en altura, equipo manual.
UPDATE tools SET category_id = 1 WHERE category_id IN (2, 3);
UPDATE tools SET category_id = 3 WHERE category_id = 4;
DELETE FROM categories WHERE id = 4;
UPDATE categories SET name = 'Eléctricas', icon = '↯' WHERE id = 1;
UPDATE categories SET name = 'Iluminación', icon = '✦' WHERE id = 2;
UPDATE categories SET name = 'Manuales', icon = '⚒' WHERE id = 3;
