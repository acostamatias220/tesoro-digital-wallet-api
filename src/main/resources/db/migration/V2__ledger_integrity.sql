-- Integrity rules enforced by the database itself, independent of the application code.

-- 1) The ledger is append-only: journal entries and postings can never be updated or deleted.
--    Corrections are made with new, compensating entries.
CREATE FUNCTION ledger_reject_mutation() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'ledger is append-only: % on % is not allowed', TG_OP, TG_TABLE_NAME
        USING ERRCODE = 'restrict_violation';
END;
$$;

CREATE TRIGGER trg_journal_entries_append_only
    BEFORE UPDATE OR DELETE ON journal_entries
    FOR EACH ROW EXECUTE FUNCTION ledger_reject_mutation();

CREATE TRIGGER trg_postings_append_only
    BEFORE UPDATE OR DELETE ON postings
    FOR EACH ROW EXECUTE FUNCTION ledger_reject_mutation();

-- 2) Every journal entry balances per currency. The check is a DEFERRED constraint trigger, so
--    it runs at COMMIT, once all postings of the entry have been inserted.
CREATE FUNCTION ledger_check_entry_balanced() RETURNS trigger
    LANGUAGE plpgsql AS
$$
DECLARE
    unbalanced RECORD;
BEGIN
    SELECT currency,
           SUM(CASE WHEN direction = 'DEBIT' THEN amount ELSE -amount END) AS difference
    INTO unbalanced
    FROM postings
    WHERE journal_entry_id = NEW.journal_entry_id
    GROUP BY currency
    HAVING SUM(CASE WHEN direction = 'DEBIT' THEN amount ELSE -amount END) <> 0
    LIMIT 1;

    IF FOUND THEN
        RAISE EXCEPTION 'journal entry % does not balance in %: difference %',
            NEW.journal_entry_id, unbalanced.currency, unbalanced.difference
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER trg_postings_balanced
    AFTER INSERT ON postings
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION ledger_check_entry_balanced();
