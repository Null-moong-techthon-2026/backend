-- Scope operator uploads to their booth; preserve all existing event-level media.
ALTER TABLE media_assets ADD COLUMN event_booth_id uuid;
ALTER TABLE media_assets ADD CONSTRAINT fk_media_booth
    FOREIGN KEY (event_booth_id, event_id) REFERENCES event_booths(id, event_id) ON DELETE RESTRICT;

CREATE TABLE booth_photos (
    event_id uuid NOT NULL,
    event_booth_id uuid NOT NULL,
    media_asset_id uuid NOT NULL,
    sort_order integer NOT NULL CHECK (sort_order BETWEEN 0 AND 9),
    PRIMARY KEY (event_booth_id, media_asset_id),
    UNIQUE (event_booth_id, sort_order),
    FOREIGN KEY (event_booth_id, event_id) REFERENCES event_booths(id, event_id) ON DELETE RESTRICT,
    FOREIGN KEY (media_asset_id, event_id) REFERENCES media_assets(id, event_id) ON DELETE RESTRICT
);
CREATE INDEX idx_booth_photos_asset ON booth_photos(media_asset_id);
