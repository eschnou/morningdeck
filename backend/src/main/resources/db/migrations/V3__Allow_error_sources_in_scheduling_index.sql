-- Update the partial index to include ERROR sources so they continue to be polled
DROP INDEX IF EXISTS idx_sources_fetch_scheduling;
CREATE INDEX idx_sources_fetch_scheduling ON sources(fetch_status, last_fetched_at, refresh_interval_minutes) WHERE status IN ('ACTIVE', 'ERROR');
