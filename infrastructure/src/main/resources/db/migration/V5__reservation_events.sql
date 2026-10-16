CREATE TABLE reservation_events (id BIGINT AUTO_INCREMENT PRIMARY KEY, reservation_id BIGINT NOT NULL, action VARCHAR(20) NOT NULL CHECK(action IN ('SOLICITADA','ACEPTADA','RECHAZADA','CANCELADA','VENCIDA')), actor_id BIGINT, actor_role VARCHAR(20) NOT NULL CHECK(actor_role IN ('CLIENTE','PROVEEDOR','ADMINISTRADOR','SISTEMA')), occurred_at DATETIME(6) NOT NULL, detail VARCHAR(500), CHECK((actor_role = 'SISTEMA') = (actor_id IS NULL)), FOREIGN KEY(reservation_id) REFERENCES reservations(id), FOREIGN KEY(actor_id) REFERENCES users(id));
CREATE INDEX idx_reservation_events_reservation ON reservation_events(reservation_id, occurred_at);
CREATE INDEX idx_reservation_events_time ON reservation_events(occurred_at);
CREATE INDEX idx_reservations_requested ON reservations(requested_at);
INSERT INTO reservation_events (reservation_id, action, actor_id, actor_role, occurred_at, detail) SELECT id, 'SOLICITADA', client_id, 'CLIENTE', requested_at, NULL FROM reservations;
CREATE TABLE report_downloads (id BIGINT AUTO_INCREMENT PRIMARY KEY, admin_id BIGINT NOT NULL, report VARCHAR(20) NOT NULL, range_from DATE NOT NULL, range_to DATE NOT NULL, downloaded_at DATETIME(6) NOT NULL, FOREIGN KEY(admin_id) REFERENCES users(id));
CREATE INDEX idx_report_downloads_time ON report_downloads(downloaded_at);
