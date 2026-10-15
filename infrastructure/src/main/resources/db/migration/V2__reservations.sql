CREATE TABLE reservations (id BIGINT AUTO_INCREMENT PRIMARY KEY, client_id BIGINT NOT NULL, tool_id BIGINT NOT NULL, start_date DATE NOT NULL, end_date DATE NOT NULL, quantity INT NOT NULL CHECK(quantity BETWEEN 1 AND 20), daily_rate DECIMAL(14,2) NOT NULL CHECK(daily_rate>0), total DECIMAL(14,2) NOT NULL CHECK(total>0), status VARCHAR(20) NOT NULL CHECK(status IN ('SOLICITADA','ACEPTADA','RECHAZADA','CANCELADA','VENCIDA')), requested_at DATETIME(6) NOT NULL, respond_by DATETIME(6), note VARCHAR(500), CHECK(end_date>start_date), FOREIGN KEY(client_id) REFERENCES users(id), FOREIGN KEY(tool_id) REFERENCES tools(id));
CREATE TABLE reservation_units (reservation_id BIGINT NOT NULL, unit_id BIGINT NOT NULL, PRIMARY KEY(reservation_id,unit_id), FOREIGN KEY(reservation_id) REFERENCES reservations(id), FOREIGN KEY(unit_id) REFERENCES units(id));
CREATE INDEX idx_reservations_tool_dates ON reservations(tool_id,status,start_date,end_date);
CREATE INDEX idx_reservations_client ON reservations(client_id,status);
CREATE INDEX idx_reservations_pending ON reservations(status,respond_by);
CREATE INDEX idx_reservation_units_unit ON reservation_units(unit_id);
