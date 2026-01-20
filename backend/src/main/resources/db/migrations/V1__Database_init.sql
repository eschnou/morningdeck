-- Morning Deck Database Schema
-- Consolidated init script

-- =============================================================================
-- CORE USER & SUBSCRIPTION TABLES
-- =============================================================================

CREATE TABLE subscriptions (
    id                  UUID PRIMARY KEY,
    plan                VARCHAR(255) NOT NULL CHECK (plan IN ('FREE', 'SOLO', 'PRO')),
    credits_balance     INTEGER NOT NULL,
    monthly_credits     INTEGER NOT NULL DEFAULT 0,
    next_renewal_date   TIMESTAMPTZ NOT NULL,
    auto_renew          BOOLEAN NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ
);

CREATE TABLE invite_codes (
    id          UUID PRIMARY KEY,
    code        VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255),
    max_uses    INTEGER,
    use_count   INTEGER NOT NULL DEFAULT 0,
    enabled     BOOLEAN NOT NULL DEFAULT TRUE,
    expires_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_invite_codes_code ON invite_codes(code);
CREATE INDEX idx_invite_codes_enabled ON invite_codes(enabled);

CREATE TABLE users (
    id              UUID PRIMARY KEY,
    username        VARCHAR(255) NOT NULL UNIQUE,
    email           VARCHAR(255) NOT NULL UNIQUE,
    name            VARCHAR(255) NOT NULL,
    password        VARCHAR(255) NOT NULL,
    avatar_url      VARCHAR(255),
    language        VARCHAR(255) NOT NULL DEFAULT 'ENGLISH',
    role            VARCHAR(255) NOT NULL DEFAULT 'USER',
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    email_verified  BOOLEAN NOT NULL DEFAULT FALSE,
    subscription_id UUID UNIQUE REFERENCES subscriptions(id),
    invite_code_id  UUID REFERENCES invite_codes(id),
    created_at      TIMESTAMPTZ NOT NULL
);

CREATE TABLE email_verification_tokens (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(255) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_email_verification_tokens_user_id ON email_verification_tokens(user_id);
CREATE INDEX idx_email_verification_tokens_expires_at ON email_verification_tokens(expires_at);

CREATE TABLE credit_usage_logs (
    id              UUID PRIMARY KEY,
    user_id         UUID REFERENCES users(id) ON DELETE SET NULL,
    subscription_id UUID REFERENCES subscriptions(id) ON DELETE SET NULL,
    credits_used    INTEGER NOT NULL,
    used_at         TIMESTAMPTZ NOT NULL
);

CREATE TABLE waitlist (
    id         UUID PRIMARY KEY,
    email      VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_waitlist_email ON waitlist(email);

-- =============================================================================
-- BRIEFING TABLES
-- =============================================================================

CREATE TABLE day_briefs (
    id                      UUID PRIMARY KEY,
    user_id                 UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title                   VARCHAR(255) NOT NULL,
    description             VARCHAR(1024),
    briefing                TEXT NOT NULL,
    frequency               VARCHAR(50) NOT NULL,
    schedule_day_of_week    VARCHAR(10),
    schedule_time           TIME NOT NULL,
    timezone                VARCHAR(100) NOT NULL DEFAULT 'UTC',
    status                  VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    email_delivery_enabled  BOOLEAN NOT NULL DEFAULT FALSE,
    position                INTEGER NOT NULL DEFAULT 0,
    last_executed_at        TIMESTAMPTZ,
    queued_at               TIMESTAMPTZ,
    processing_started_at   TIMESTAMPTZ,
    error_message           VARCHAR(1024),
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ
);

CREATE INDEX idx_day_briefs_user_status ON day_briefs(user_id, status);
CREATE INDEX idx_day_briefs_user_position ON day_briefs(user_id, position);
CREATE INDEX idx_day_briefs_schedule ON day_briefs(status, schedule_time);
CREATE INDEX idx_day_briefs_scheduling ON day_briefs(status, schedule_time, last_executed_at) WHERE status = 'ACTIVE';
CREATE INDEX idx_day_briefs_stuck_recovery ON day_briefs(status, queued_at, processing_started_at) WHERE status IN ('QUEUED', 'PROCESSING');

-- =============================================================================
-- SOURCE TABLES
-- =============================================================================

CREATE TABLE sources (
    id                       UUID PRIMARY KEY,
    day_brief_id             UUID NOT NULL REFERENCES day_briefs(id) ON DELETE CASCADE,
    name                     VARCHAR(255) NOT NULL,
    url                      VARCHAR(2048) NOT NULL,
    type                     VARCHAR(50) NOT NULL,
    status                   VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    tags                     TEXT,
    extraction_prompt        VARCHAR(2048),
    email_address            UUID UNIQUE,
    -- Fetch queue fields
    fetch_status             VARCHAR(50) NOT NULL DEFAULT 'IDLE',
    refresh_interval_minutes INTEGER NOT NULL DEFAULT 15,
    queued_at                TIMESTAMPTZ,
    fetch_started_at         TIMESTAMPTZ,
    last_fetched_at          TIMESTAMPTZ,
    last_error               VARCHAR(1024),
    etag                     VARCHAR(255),
    last_modified            VARCHAR(255),
    created_at               TIMESTAMPTZ NOT NULL,
    updated_at               TIMESTAMPTZ,
    UNIQUE(day_brief_id, url)
);

CREATE INDEX idx_sources_daybrief ON sources(day_brief_id);
CREATE INDEX idx_sources_status ON sources(status);
CREATE INDEX idx_sources_email_address ON sources(email_address) WHERE email_address IS NOT NULL;
CREATE INDEX idx_sources_fetch_scheduling ON sources(fetch_status, last_fetched_at, refresh_interval_minutes) WHERE status = 'ACTIVE';

CREATE TABLE raw_emails (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id    UUID NOT NULL REFERENCES sources(id) ON DELETE CASCADE,
    message_id   VARCHAR(512) NOT NULL,
    from_address VARCHAR(512) NOT NULL,
    subject      VARCHAR(1024) NOT NULL,
    raw_content  TEXT NOT NULL,
    received_at  TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(source_id, message_id)
);

CREATE INDEX idx_raw_emails_source_id ON raw_emails(source_id);
CREATE INDEX idx_raw_emails_created_at ON raw_emails(created_at);

-- =============================================================================
-- NEWS ITEM TABLES
-- =============================================================================

CREATE TABLE news_items (
    id              UUID PRIMARY KEY,
    source_id       UUID NOT NULL REFERENCES sources(id) ON DELETE CASCADE,
    guid            VARCHAR(512) NOT NULL,
    title           VARCHAR(1024) NOT NULL,
    link            VARCHAR(2048) NOT NULL,
    author          VARCHAR(255),
    published_at    TIMESTAMPTZ,
    raw_content     TEXT,
    clean_content   TEXT,
    web_content     TEXT,
    summary         TEXT,
    tags            TEXT,
    score           INTEGER,
    score_reasoning VARCHAR(512),
    status          VARCHAR(50) NOT NULL DEFAULT 'NEW',
    error_message   VARCHAR(1024),
    read_at         TIMESTAMPTZ,
    saved           BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ,
    UNIQUE(source_id, guid)
);

CREATE INDEX idx_news_items_source_guid ON news_items(source_id, guid);
CREATE INDEX idx_news_items_source_published ON news_items(source_id, published_at DESC);
CREATE INDEX idx_news_items_status ON news_items(status);
CREATE INDEX idx_news_items_created ON news_items(created_at);
CREATE INDEX idx_news_items_source_read ON news_items(source_id, read_at);
CREATE INDEX idx_news_items_source_saved ON news_items(source_id, saved);
CREATE INDEX idx_news_items_score ON news_items(score) WHERE score IS NOT NULL;
CREATE INDEX idx_news_items_published_at ON news_items(published_at DESC);
CREATE INDEX idx_news_items_unread ON news_items(source_id, published_at DESC) WHERE read_at IS NULL;
CREATE INDEX idx_news_items_scheduling ON news_items(status, created_at) WHERE status = 'NEW';
CREATE INDEX idx_news_items_stuck_recovery ON news_items(status, updated_at) WHERE status IN ('PENDING', 'PROCESSING');

-- =============================================================================
-- REPORT TABLES
-- =============================================================================

CREATE TABLE daily_reports (
    id           UUID PRIMARY KEY,
    day_brief_id UUID NOT NULL REFERENCES day_briefs(id) ON DELETE CASCADE,
    generated_at TIMESTAMPTZ NOT NULL,
    status       VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    created_at   TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_daily_reports_daybrief ON daily_reports(day_brief_id, generated_at DESC);

CREATE TABLE report_items (
    id           UUID PRIMARY KEY,
    report_id    UUID NOT NULL REFERENCES daily_reports(id) ON DELETE CASCADE,
    news_item_id UUID NOT NULL REFERENCES news_items(id) ON DELETE CASCADE,
    score        INTEGER NOT NULL,
    position     INTEGER NOT NULL
);

CREATE INDEX idx_report_items_report ON report_items(report_id, position);

-- =============================================================================
-- API USAGE TRACKING
-- =============================================================================

CREATE TABLE api_usage_logs (
    id            UUID PRIMARY KEY,
    user_id       UUID REFERENCES users(id) ON DELETE SET NULL,
    feature_key   VARCHAR(32) NOT NULL,
    model         VARCHAR(64),
    input_tokens  BIGINT,
    output_tokens BIGINT,
    total_tokens  BIGINT,
    success       BOOLEAN NOT NULL,
    error_message VARCHAR(1024),
    duration_ms   BIGINT NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_api_usage_user_id ON api_usage_logs(user_id);
CREATE INDEX idx_api_usage_feature ON api_usage_logs(feature_key);
CREATE INDEX idx_api_usage_created_at ON api_usage_logs(created_at);
CREATE INDEX idx_api_usage_user_created ON api_usage_logs(user_id, created_at DESC);
