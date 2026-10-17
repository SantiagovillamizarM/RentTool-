ALTER TABLE tool_images ADD COLUMN position INT NOT NULL DEFAULT 0;
CREATE INDEX idx_tool_images_order ON tool_images(tool_id, position, id);
