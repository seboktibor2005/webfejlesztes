ALTER TABLE equipment_category ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE storage_location ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
CREATE UNIQUE INDEX uk_category_normalized_name ON equipment_category(lower(btrim(name)));
CREATE UNIQUE INDEX uk_location_normalized_code ON storage_location(upper(btrim(code)));
