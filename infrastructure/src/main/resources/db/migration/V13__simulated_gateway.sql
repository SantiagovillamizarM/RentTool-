-- Pasarela simulada: método y detalle enmascarado (por ejemplo «Visa •••• 4242»), nunca el número
-- completo ni el CVV. message guarda el motivo del rechazo o del reembolso.
ALTER TABLE payments ADD COLUMN method VARCHAR(20);
ALTER TABLE payments ADD COLUMN detail VARCHAR(60);
ALTER TABLE payments ADD COLUMN message VARCHAR(200);
