-- =========================================================
-- Fakturace hradu po měsících: správce si označí, že měsíc vyfakturoval a kdy. Fakturuje se vždy až po
-- skončení měsíce (invoiced_on nejdřív první den následujícího měsíce).
-- month: první den fakturovaného měsíce; amount_czk: částka k fakturaci v okamžiku označení (pro kontrolu,
-- kdyby se později změnila cena nebo podíl s datem v minulosti).
-- =========================================================
CREATE TABLE partner_invoice (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    partner_id   UUID          NOT NULL REFERENCES partner(id) ON DELETE CASCADE,
    month        DATE          NOT NULL CHECK (extract(DAY FROM month) = 1),
    invoiced_on  DATE          NOT NULL,
    amount_czk   NUMERIC(12,2) NOT NULL,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_partner_invoice_month UNIQUE (partner_id, month),
    CONSTRAINT ck_partner_invoice_after_month CHECK (invoiced_on >= month + INTERVAL '1 month')
);
