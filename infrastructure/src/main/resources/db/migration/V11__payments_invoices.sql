CREATE TABLE payments (
 reference VARCHAR(50) PRIMARY KEY,
 reservation_id BIGINT NOT NULL,
 amount DECIMAL(14,2) NOT NULL CHECK(amount > 0),
 status VARCHAR(20) NOT NULL CHECK(status IN ('PENDING','APPROVED','DECLINED','ERROR','VOIDED','REVIEW')),
 transaction_id VARCHAR(100) UNIQUE,
 created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 FOREIGN KEY(reservation_id) REFERENCES reservations(id)
);
CREATE INDEX idx_payments_reservation ON payments(reservation_id,created_at);
CREATE TABLE invoices (
 reservation_id BIGINT PRIMARY KEY,
 number VARCHAR(50) NOT NULL UNIQUE,
 payment_reference VARCHAR(50) NOT NULL UNIQUE,
 payload TEXT NOT NULL,
 FOREIGN KEY(reservation_id) REFERENCES reservations(id),
 FOREIGN KEY(payment_reference) REFERENCES payments(reference)
);
