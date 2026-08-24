ALTER TABLE procurement.suppliers
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
