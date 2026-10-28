-- =========================================================
-- Firemní údaje partnera a předání dávky kódů partnerovi
-- =========================================================
ALTER TABLE partner
    ADD COLUMN company_id     VARCHAR(20),    -- IČ
    ADD COLUMN vat_id         VARCHAR(20),    -- DIČ
    ADD COLUMN contact_person VARCHAR(200),
    ADD COLUMN phone          VARCHAR(50);
CREATE UNIQUE INDEX uq_partner_company_id ON partner(company_id) WHERE company_id IS NOT NULL;

-- Kódy dávky fungují až po předání partnerovi (do té doby jsou ve výrobě a nesmí jít použít).
ALTER TABLE access_code_batch ADD COLUMN handed_over_at TIMESTAMPTZ;

-- Dosavadní dávky už jsou v oběhu
UPDATE access_code_batch SET handed_over_at = created_at;
