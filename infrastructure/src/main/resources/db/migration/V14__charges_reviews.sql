-- Recargo por devolución tardía: días de retraso y su importe a la tarifa diaria pactada.
ALTER TABLE rental_operations ADD COLUMN late_days INT NOT NULL DEFAULT 0;
ALTER TABLE rental_operations ADD COLUMN late_fee DECIMAL(14,2) NOT NULL DEFAULT 0;
-- Cobro por una unidad devuelta dañada o perdida, fijado por quien revisa la devolución.
ALTER TABLE rental_returns ADD COLUMN damage_charge DECIMAL(14,2) NOT NULL DEFAULT 0;
-- Calificación del cliente a la herramienta de una reserva ya devuelta: una por reserva.
CREATE TABLE tool_reviews (id BIGINT AUTO_INCREMENT PRIMARY KEY, reservation_id BIGINT NOT NULL UNIQUE, tool_id BIGINT NOT NULL, client_id BIGINT NOT NULL, rating INT NOT NULL CHECK(rating BETWEEN 1 AND 5), review_comment VARCHAR(500), created_at DATETIME(6) NOT NULL, FOREIGN KEY(reservation_id) REFERENCES reservations(id), FOREIGN KEY(tool_id) REFERENCES tools(id), FOREIGN KEY(client_id) REFERENCES users(id));
CREATE INDEX idx_tool_reviews_tool ON tool_reviews(tool_id, created_at);
