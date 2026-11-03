-- =========================================================
-- Ukázková data do administrace, JEN pro lokální vývoj (nikdy na testovací ani ostrý server).
--
-- Jediný partner „Hrad Loket“ s oběma demo hrami, dávky kartiček, aktivace od září do dneška
-- (víc o víkendech, mezi 9. a 17. hodinou) a k nim odehrané hry se stanovišti, nápovědami a špatnými
-- odpověďmi. Dětské hře dochází kartičky (upozornění), u Katovy povinnosti je inventura a dávka ve výrobě.
-- Podíl hradu 30 %, kartičky za 69 Kč a 49 Kč, září vyfakturované.
-- Asi třetina her Katovy povinnosti se hraje v německé verzi (kartičky jsou stejné, jazyk volí hráč).
--
-- Spuštění (lokální PostgreSQL z docker-compose.yml, server může běžet):
--   docker exec -i zazitky-postgres psql -U zazitky -d zazitky -v ON_ERROR_STOP=1 < tools/dev/admin-demo-data.sql
--
-- Opakovatelné: nejprve smaže svá data (dávky 00000000-0000-0009-…) a ostatní partnery. Když se změní
-- seed demo her, seed tato data smaže a partner se zase jmenuje „Demo partner“; pak skript spusť znovu.
-- =========================================================
BEGIN;

-- Jen jeden partner: ostatní (např. ručně založené v administraci) pryč i s jejich daty
DELETE FROM game_session WHERE access_code_id IN (
    SELECT c.id FROM access_code c JOIN access_code_batch b ON b.id = c.batch_id
    WHERE b.partner_id <> '00000000-0000-0000-0000-000000000001'
       OR b.id::text LIKE '00000000-0000-0009-%');
DELETE FROM access_code WHERE batch_id IN (
    SELECT id FROM access_code_batch
    WHERE partner_id <> '00000000-0000-0000-0000-000000000001' OR id::text LIKE '00000000-0000-0009-%');
DELETE FROM access_code_batch
WHERE partner_id <> '00000000-0000-0000-0000-000000000001' OR id::text LIKE '00000000-0000-0009-%';
DELETE FROM partner_stock_count;
DELETE FROM partner_user WHERE partner_id <> '00000000-0000-0000-0000-000000000001';
DELETE FROM partner_share WHERE partner_id <> '00000000-0000-0000-0000-000000000001';
DELETE FROM location WHERE partner_id <> '00000000-0000-0000-0000-000000000001';
DELETE FROM partner WHERE id <> '00000000-0000-0000-0000-000000000001';

UPDATE partner
SET name = 'Hrad Loket', company_id = '12345678', vat_id = 'CZ12345678', contact_person = 'Jan Kastelán',
    contact_email = 'pokladna@hrad-loket.example', phone = '+420 352 000 000'
WHERE id = '00000000-0000-0000-0000-000000000001';
UPDATE location SET name = 'Hrad Loket' WHERE id = '00000000-0000-0000-0000-000000000002';

-- Podíl hradu 30 % od začátku roku
DELETE FROM partner_share WHERE partner_id = '00000000-0000-0000-0000-000000000001';
INSERT INTO partner_share (partner_id, valid_from, share_pct)
VALUES ('00000000-0000-0000-0000-000000000001', DATE '2026-01-01', 30.00);

-- Ceny kartiček (game_price) od začátku roku: Katova povinnost 69 Kč, Poklad mistra Husse 49 Kč
DELETE FROM game_price WHERE game_id IN ('00000000-0000-0000-0000-000000000003', '00000000-0000-0001-0000-000000000003');
INSERT INTO game_price (game_id, valid_from, price_czk)
VALUES ('00000000-0000-0000-0000-000000000003', DATE '2026-01-01', 69),
       ('00000000-0000-0001-0000-000000000003', DATE '2026-01-01', 49);

-- Fakturace: září je vyfakturované, říjen čeká (částku září dopočítá dotaz níže po vložení aktivací)
DELETE FROM partner_invoice WHERE partner_id = '00000000-0000-0000-0000-000000000001';

-- Dávky kartiček, platnost kódu 5 hodin
INSERT INTO access_code_batch (id, partner_id, game_id, quantity, note, created_at,
                               validity_hours, multi_session, handed_over_at)
VALUES ('00000000-0000-0009-0000-000000000001', '00000000-0000-0000-0000-000000000001',
        '00000000-0000-0000-0000-000000000003', 200, 'Zahájení sezóny', TIMESTAMPTZ '2026-08-20 10:00+02',
        5, FALSE, TIMESTAMPTZ '2026-08-28 14:00+02'),
       ('00000000-0000-0009-0000-000000000002', '00000000-0000-0000-0000-000000000001',
        '00000000-0000-0000-0000-000000000003', 200, 'Doplnění na podzim', TIMESTAMPTZ '2026-10-05 09:30+02',
        5, FALSE, TIMESTAMPTZ '2026-10-12 11:00+02'),
       ('00000000-0000-0009-0000-000000000003', '00000000-0000-0000-0000-000000000001',
        '00000000-0000-0000-0000-000000000003', 150, 'Na Vánoce (v tisku)', TIMESTAMPTZ '2026-11-02 08:00+01',
        5, FALSE, NULL),
       ('00000000-0000-0009-0000-000000000004', '00000000-0000-0000-0000-000000000001',
        '00000000-0000-0001-0000-000000000003', 100, 'Dětská hra, první dávka', TIMESTAMPTZ '2026-09-25 10:00+02',
        5, FALSE, TIMESTAMPTZ '2026-10-02 13:00+02');

-- Kódy: 8 znaků z abecedy AccessCodeFormat, odvozené z čísla dávky a pořadí (stabilní mezi spuštěními)
INSERT INTO access_code (batch_id, code, status)
SELECT b.id,
       (SELECT string_agg(substr('ABCDEFGHJKMNPQRSTUVWXYZ23456789',
                                 1 + get_byte(decode(md5(b.id::text || ':' || n), 'hex'), k) % 31, 1), '' ORDER BY k)
        FROM generate_series(0, 7) k),
       'NEW'
FROM access_code_batch b, generate_series(1, b.quantity) n
WHERE b.id::text LIKE '00000000-0000-0009-%'
ON CONFLICT (code) DO NOTHING;

-- Jedna odehraná hra k aktivovanému kódu
CREATE FUNCTION pg_temp.play(p_code UUID, p_game UUID, p_start TIMESTAMPTZ, p_now TIMESTAMPTZ) RETURNS VOID
LANGUAGE plpgsql AS $f$
DECLARE
    kids     BOOLEAN := p_game = '00000000-0000-0001-0000-000000000003';
    teams    TEXT[]  := ARRAY['Novákovi', 'Rytíři z Plzně', 'Svobodovi', 'Kryštofovi', 'Dvořákovi', 'Parta z Karlovarska',
                              'Černí', 'Procházkovi', 'Kuchtovi', 'Babička a vnoučata', 'Horákovi', 'Tým Ohře',
                              'Anička a Tomáš', 'Veselí', 'Marešovi', 'Pospíšilovi', 'Kateřina', 'Výletníci'];
    sess     UUID    := gen_random_uuid();
    outcome  TEXT;
    stop_at  INT;
    total    INT;
    cur      TIMESTAMPTZ := p_start;
    st       RECORD;
    dur      INTERVAL;
    hints    INT;
    wrong    INT;
    penalty  INT := 0;
    finished TIMESTAMPTZ;
    r        FLOAT := random();
BEGIN
    SELECT count(*) INTO total FROM station WHERE game_id = p_game;
    outcome := CASE WHEN r < 0.84 THEN 'COMPLETED' WHEN r < 0.92 THEN 'ABANDONED' ELSE 'EXPIRED' END;
    stop_at := CASE WHEN outcome = 'COMPLETED' THEN total + 1 ELSE 2 + floor(random() * (total - 2))::INT END;

    INSERT INTO game_session (id, game_id, access_code_id, token, team_name, status, penalty_seconds, started_at,
                              expires_at)
    VALUES (sess, p_game, p_code, gen_random_uuid(),
            CASE WHEN random() < 0.85 THEN teams[1 + floor(random() * array_length(teams, 1))::INT] END,
            'IN_PROGRESS', 0, p_start, p_start + INTERVAL '5 hours');

    FOR st IN
        SELECT s.id, s.order_no, count(h.id) AS hint_count, coalesce(min(h.penalty_seconds), 0) AS penalty
        FROM station s LEFT JOIN hint h ON h.station_id = s.id
        WHERE s.game_id = p_game GROUP BY s.id, s.order_no ORDER BY s.order_no
    LOOP
        dur := make_interval(secs => CASE WHEN kids THEN 120 + random() * 240 ELSE 180 + random() * 420 END);
        hints := CASE WHEN random() < CASE WHEN st.hint_count > 1 THEN 0.45 ELSE 0.2 END
                      THEN 1 + floor(random() * st.hint_count)::INT ELSE 0 END;
        wrong := CASE WHEN random() < 0.35 THEN 1 + floor(random() * 3)::INT ELSE 0 END;

        IF st.order_no = stop_at OR cur + dur > p_now THEN
            -- Here the team stopped (or is still thinking)
            INSERT INTO session_progress (session_id, station_id, status, hints_used, attempts_count, activated_at)
            VALUES (sess, st.id, 'ACTIVE', hints, wrong, cur);
            penalty := penalty + hints * st.penalty;
            IF cur + dur > p_now THEN
                outcome := 'IN_PROGRESS';
            END IF;
            EXIT;
        END IF;

        INSERT INTO session_progress (session_id, station_id, status, hints_used, attempts_count, activated_at,
                                      solved_at)
        VALUES (sess, st.id, 'SOLVED', hints, wrong + 1, cur, cur + dur);
        penalty := penalty + hints * st.penalty;
        cur := cur + dur;
        IF st.order_no = total THEN
            finished := cur;
        END IF;
    END LOOP;

    -- Kdo nedohrál a kódu ještě běží platnost, pořád hraje
    IF outcome IN ('ABANDONED', 'EXPIRED') AND p_start + INTERVAL '5 hours' > p_now THEN
        outcome := 'IN_PROGRESS';
    END IF;

    UPDATE game_session
    SET status = outcome, penalty_seconds = penalty, finished_at = CASE WHEN outcome = 'COMPLETED' THEN finished END
    WHERE id = sess;
    UPDATE access_code
    SET status = CASE WHEN outcome = 'EXPIRED' THEN 'EXPIRED' ELSE 'ACTIVATED' END,
        activated_at = p_start, valid_until = p_start + INTERVAL '5 hours'
    WHERE id = p_code;
END
$f$;

-- Aktivace den po dni: o víkendu víc, v říjnu méně než v září, dětská hra od října
DO $$
DECLARE
    now_ts  TIMESTAMPTZ := now();
    d       DATE;
    g       UUID;
    rate    FLOAT;
    n       INT;
    t       TIMESTAMPTZ;
    code_id UUID;
    weekend BOOLEAN;
BEGIN
    PERFORM setseed(0.42);
    FOR d IN SELECT generate_series(DATE '2026-09-01', current_date, INTERVAL '1 day')::DATE LOOP
        weekend := extract(isodow FROM d) >= 6;
        FOREACH g IN ARRAY ARRAY['00000000-0000-0000-0000-000000000003',
                                 '00000000-0000-0001-0000-000000000003']::UUID[] LOOP
            IF g = '00000000-0000-0000-0000-000000000003' THEN
                rate := CASE WHEN weekend THEN 8 ELSE 3 END * CASE WHEN d < DATE '2026-10-01' THEN 1 ELSE 0.75 END;
            ELSE
                rate := CASE WHEN weekend THEN 5 ELSE 1.6 END;
            END IF;
            n := floor(rate * (0.5 + random()))::INT;
            FOR i IN 1..n LOOP
                t := (d + TIME '09:00' + random() * INTERVAL '8 hours') AT TIME ZONE 'Europe/Prague';
                CONTINUE WHEN t > now_ts - INTERVAL '10 minutes';
                SELECT c.id INTO code_id
                FROM access_code c JOIN access_code_batch b ON b.id = c.batch_id
                WHERE b.game_id = g AND b.id::TEXT LIKE '00000000-0000-0009-%' AND c.status = 'NEW'
                  AND b.handed_over_at < t
                ORDER BY b.handed_over_at, c.code
                LIMIT 1;
                CONTINUE WHEN code_id IS NULL;
                PERFORM pg_temp.play(code_id, CASE WHEN g = '00000000-0000-0000-0000-000000000003' AND random() < 0.3
                                                   THEN '00000000-0000-0002-0000-000000000003'::UUID ELSE g END,
                                     t, now_ts);
            END LOOP;
        END LOOP;
    END LOOP;

    -- Tři týmy právě hrají
    FOREACH t IN ARRAY ARRAY[now_ts - INTERVAL '75 minutes', now_ts - INTERVAL '40 minutes',
                              now_ts - INTERVAL '20 minutes'] LOOP
        g := CASE WHEN t = now_ts - INTERVAL '20 minutes' THEN '00000000-0000-0001-0000-000000000003'::UUID
                  ELSE '00000000-0000-0000-0000-000000000003'::UUID END;
        SELECT c.id INTO code_id
        FROM access_code c JOIN access_code_batch b ON b.id = c.batch_id
        WHERE b.game_id = g AND b.id::TEXT LIKE '00000000-0000-0009-%' AND c.status = 'NEW'
          AND b.handed_over_at < t
        ORDER BY b.handed_over_at, c.code
        LIMIT 1;
        -- Nejstarší z nich hraje německy
        PERFORM pg_temp.play(code_id, CASE WHEN t = now_ts - INTERVAL '75 minutes'
                                           THEN '00000000-0000-0002-0000-000000000003'::UUID ELSE g END,
                             t, now_ts);
    END LOOP;
END
$$;

-- Inventura Katovy povinnosti 31. 10.: hrad našel o 2 kartičky méně (prodané, ale neaktivované)
INSERT INTO partner_stock_count (partner_id, game_id, counted_at, cards_in_stock, computed_in_stock, partner_user_id)
SELECT '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000003', at, computed - 2, computed,
       '00000000-0000-0000-0007-000000000001'
FROM (SELECT TIMESTAMPTZ '2026-10-31 17:30+01' AS at) x,
     LATERAL (SELECT count(*) FILTER (WHERE b.handed_over_at <= x.at)
                     - count(*) FILTER (WHERE c.activated_at <= x.at) AS computed
              FROM access_code c JOIN access_code_batch b ON b.id = c.batch_id
              WHERE b.game_id = '00000000-0000-0000-0000-000000000003'
                AND b.id::TEXT LIKE '00000000-0000-0009-%') y;

INSERT INTO partner_invoice (partner_id, month, invoiced_on, amount_czk)
SELECT '00000000-0000-0000-0000-000000000001', DATE '2026-09-01', DATE '2026-10-03',
       coalesce(sum(p.price_czk * 0.70), 0)
FROM access_code c
JOIN access_code_batch b ON b.id = c.batch_id
JOIN game_price p ON p.game_id = b.game_id
WHERE b.partner_id = '00000000-0000-0000-0000-000000000001' AND NOT b.multi_session
  AND (c.activated_at AT TIME ZONE 'Europe/Prague')::date BETWEEN DATE '2026-09-01' AND DATE '2026-09-30';

COMMIT;

-- Přehled
SELECT g.title, to_char(c.activated_at AT TIME ZONE 'Europe/Prague', 'YYYY-MM') AS mesic, count(*) AS aktivace,
       sum((SELECT price_czk FROM game_price p WHERE p.game_id = b.game_id
              AND p.valid_from <= (c.activated_at AT TIME ZONE 'Europe/Prague')::date
            ORDER BY p.valid_from DESC LIMIT 1)) AS trzba_czk
FROM access_code c JOIN access_code_batch b ON b.id = c.batch_id JOIN game g ON g.id = b.game_id
WHERE c.activated_at IS NOT NULL AND NOT b.multi_session
GROUP BY 1, 2 ORDER BY 1, 2;
SELECT status, count(*) AS hry FROM game_session GROUP BY 1 ORDER BY 1;
