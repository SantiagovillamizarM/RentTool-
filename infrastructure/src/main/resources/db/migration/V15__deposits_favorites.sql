-- Depósito de garantía por unidad de cada herramienta y el total pactado en cada reserva.
ALTER TABLE tools ADD COLUMN deposit DECIMAL(14,2) NOT NULL DEFAULT 0;
ALTER TABLE reservations ADD COLUMN deposit DECIMAL(14,2) NOT NULL DEFAULT 0;
-- Herramientas que cada cliente guarda para después.
CREATE TABLE favorites (client_id BIGINT NOT NULL, tool_id BIGINT NOT NULL, created_at DATETIME(6) NOT NULL, PRIMARY KEY(client_id, tool_id), FOREIGN KEY(client_id) REFERENCES users(id), FOREIGN KEY(tool_id) REFERENCES tools(id));
CREATE INDEX idx_favorites_client ON favorites(client_id, created_at);
