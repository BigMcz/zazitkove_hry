-- =========================================================
-- Účty partnerů (správa hradu) pro přehled aktivovaných kódů
-- Účet správce aplikace je v konfiguraci (ADMIN_USERNAME / ADMIN_PASSWORD), ne v DB.
-- =========================================================
CREATE TABLE partner_user (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    partner_id     UUID         NOT NULL REFERENCES partner(id) ON DELETE CASCADE,
    username       VARCHAR(100) NOT NULL UNIQUE,   -- malými písmeny
    password_hash  VARCHAR(200) NOT NULL,          -- {bcrypt}…, DelegatingPasswordEncoder
    display_name   VARCHAR(200),
    enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_login_at  TIMESTAMPTZ
);
CREATE INDEX ix_partner_user_partner ON partner_user(partner_id);
