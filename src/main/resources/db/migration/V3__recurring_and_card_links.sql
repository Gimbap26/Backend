ALTER TABLE transactions ADD COLUMN card_id BIGINT;
ALTER TABLE financial_events ADD COLUMN recurring_rule_id BIGINT;
ALTER TABLE financial_events ADD COLUMN card_id BIGINT;

ALTER TABLE transactions ADD CONSTRAINT fk_transactions_card
    FOREIGN KEY (card_id) REFERENCES cards(id);
ALTER TABLE financial_events ADD CONSTRAINT fk_financial_events_rule
    FOREIGN KEY (recurring_rule_id) REFERENCES recurring_rules(id);
ALTER TABLE financial_events ADD CONSTRAINT fk_financial_events_card
    FOREIGN KEY (card_id) REFERENCES cards(id);

-- 자동 생성 이벤트의 중복 방지.
-- NULL 은 서로 다른 값으로 취급되므로 수동 생성 이벤트는 제약에 걸리지 않는다.
ALTER TABLE financial_events ADD CONSTRAINT uq_financial_events_rule_date
    UNIQUE (recurring_rule_id, event_date);
ALTER TABLE financial_events ADD CONSTRAINT uq_financial_events_card_date
    UNIQUE (card_id, event_date);

CREATE INDEX idx_transactions_card ON transactions(card_id);
CREATE INDEX idx_financial_events_rule ON financial_events(recurring_rule_id);
