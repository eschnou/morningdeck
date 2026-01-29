-- Increase link column size to accommodate long newsletter tracking URLs
-- Some tracking URLs (e.g., beehiiv) can exceed 2048 characters

ALTER TABLE news_items ALTER COLUMN link TYPE VARCHAR(4096);
