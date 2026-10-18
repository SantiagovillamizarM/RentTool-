CREATE TABLE rental_operations (
 reservation_id BIGINT PRIMARY KEY,
 delivered_at DATETIME(6) NOT NULL,
 delivered_by BIGINT NOT NULL,
 delivery_notes VARCHAR(500),
 returned_at DATETIME(6),
 returned_by BIGINT,
 return_notes VARCHAR(500),
 CHECK((returned_at IS NULL AND returned_by IS NULL) OR
       (returned_at IS NOT NULL AND returned_by IS NOT NULL AND returned_at >= delivered_at)),
 FOREIGN KEY(reservation_id) REFERENCES reservations(id),
 FOREIGN KEY(delivered_by) REFERENCES users(id),
 FOREIGN KEY(returned_by) REFERENCES users(id)
);
CREATE TABLE rental_returns (
 reservation_id BIGINT NOT NULL,
 unit_id BIGINT NOT NULL,
 return_condition VARCHAR(20) NOT NULL CHECK(return_condition IN ('BUENO','DANADO','PERDIDO')),
 notes VARCHAR(500),
 PRIMARY KEY(reservation_id,unit_id),
 FOREIGN KEY(reservation_id) REFERENCES rental_operations(reservation_id),
 FOREIGN KEY(unit_id) REFERENCES units(id)
);
CREATE TABLE incidents (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 reservation_id BIGINT NOT NULL,
 unit_id BIGINT,
 incident_type VARCHAR(20) NOT NULL CHECK(incident_type IN ('DANO','PERDIDA','OTRO')),
 description VARCHAR(1000) NOT NULL,
 reported_by BIGINT NOT NULL,
 reported_at DATETIME(6) NOT NULL,
 resolved_by BIGINT,
 resolved_at DATETIME(6),
 resolution VARCHAR(1000),
 CHECK((resolved_at IS NULL AND resolved_by IS NULL AND resolution IS NULL) OR
       (resolved_at IS NOT NULL AND resolved_by IS NOT NULL AND resolution IS NOT NULL)),
 CHECK(incident_type = 'OTRO' OR unit_id IS NOT NULL),
 FOREIGN KEY(reservation_id) REFERENCES rental_operations(reservation_id),
 FOREIGN KEY(unit_id) REFERENCES units(id),
 FOREIGN KEY(reported_by) REFERENCES users(id),
 FOREIGN KEY(resolved_by) REFERENCES users(id)
);
CREATE INDEX idx_incidents_unit_open ON incidents(unit_id,resolved_at);
CREATE INDEX idx_incidents_reservation ON incidents(reservation_id,reported_at);
CREATE INDEX idx_incidents_open ON incidents(resolved_at,reported_at);
CREATE INDEX idx_operations_returned ON rental_operations(returned_at);
