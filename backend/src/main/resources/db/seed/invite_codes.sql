-- Seed invite codes for testing
-- Run this manually against your local database to set up test codes:
-- psql -h localhost -U postgres -d morningdeck -f backend/src/main/resources/db/seed/invite_codes.sql

-- Unlimited use code
INSERT INTO invite_codes (id, code, description, max_uses, use_count, enabled, expires_at, created_at)
VALUES (gen_random_uuid(), 'BETA2026', 'Unlimited beta access code', NULL, 0, true, NULL, NOW())
ON CONFLICT (code) DO NOTHING;

-- Limited use code (10 uses)
INSERT INTO invite_codes (id, code, description, max_uses, use_count, enabled, expires_at, created_at)
VALUES (gen_random_uuid(), 'LIMITED10', 'Limited to 10 uses', 10, 0, true, NULL, NOW())
ON CONFLICT (code) DO NOTHING;

-- Disabled code (for testing rejection)
INSERT INTO invite_codes (id, code, description, max_uses, use_count, enabled, expires_at, created_at)
VALUES (gen_random_uuid(), 'DISABLED', 'Disabled code for testing', NULL, 0, false, NULL, NOW())
ON CONFLICT (code) DO NOTHING;

-- Expired code (for testing rejection)
INSERT INTO invite_codes (id, code, description, max_uses, use_count, enabled, expires_at, created_at)
VALUES (gen_random_uuid(), 'EXPIRED', 'Expired code for testing', NULL, 0, true, NOW() - INTERVAL '1 day', NOW())
ON CONFLICT (code) DO NOTHING;

-- Exhausted code (for testing rejection)
INSERT INTO invite_codes (id, code, description, max_uses, use_count, enabled, expires_at, created_at)
VALUES (gen_random_uuid(), 'EXHAUSTED', 'Exhausted code for testing', 1, 1, true, NULL, NOW())
ON CONFLICT (code) DO NOTHING;

SELECT code, description, max_uses, use_count, enabled, expires_at FROM invite_codes;
