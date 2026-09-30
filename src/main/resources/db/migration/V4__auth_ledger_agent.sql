-- 인증: 이메일/비밀번호 로그인
ALTER TABLE users ADD COLUMN email VARCHAR(255);
ALTER TABLE users ADD COLUMN password_hash VARCHAR(255);
ALTER TABLE users ADD CONSTRAINT uq_users_email UNIQUE (email);
-- 가입자 ID. 시드 사용자(1번)와 겹치지 않도록 1000부터 발급한다.
CREATE SEQUENCE users_id_seq START WITH 1000;

-- 잔액 원장: 거래와 결제 완료된 이벤트가 어느 계좌의 잔액을 바꾸는지 기록한다.
ALTER TABLE accounts ADD COLUMN version BIGINT DEFAULT 0 NOT NULL;
ALTER TABLE transactions ADD COLUMN account_id BIGINT;
ALTER TABLE transactions ADD COLUMN transfer_account_id BIGINT;
ALTER TABLE financial_events ADD COLUMN account_id BIGINT;
ALTER TABLE cards ADD COLUMN payment_account_id BIGINT;
ALTER TABLE recurring_rules ADD COLUMN account_id BIGINT;

ALTER TABLE transactions ADD CONSTRAINT fk_transactions_account
    FOREIGN KEY (account_id) REFERENCES accounts(id);
ALTER TABLE transactions ADD CONSTRAINT fk_transactions_transfer_account
    FOREIGN KEY (transfer_account_id) REFERENCES accounts(id);
ALTER TABLE financial_events ADD CONSTRAINT fk_financial_events_account
    FOREIGN KEY (account_id) REFERENCES accounts(id);
ALTER TABLE cards ADD CONSTRAINT fk_cards_payment_account
    FOREIGN KEY (payment_account_id) REFERENCES accounts(id);
ALTER TABLE recurring_rules ADD CONSTRAINT fk_recurring_rules_account
    FOREIGN KEY (account_id) REFERENCES accounts(id);

CREATE INDEX idx_transactions_account ON transactions(account_id);
CREATE INDEX idx_transactions_transfer_account ON transactions(transfer_account_id);
CREATE INDEX idx_financial_events_account ON financial_events(account_id);
CREATE INDEX idx_cards_payment_account ON cards(payment_account_id);
CREATE INDEX idx_recurring_rules_account ON recurring_rules(account_id);

-- AI 답변 근거 보존: 답변 당시 호출한 도구의 인자와 결과를 그대로 남긴다.
-- TEXT/CLOB 은 H2 와 PostgreSQL 에서 Hibernate 검증 결과가 달라 큰 VARCHAR 를 쓴다.
ALTER TABLE agent_messages ALTER COLUMN content SET DATA TYPE VARCHAR(20000);
ALTER TABLE agent_messages ADD COLUMN tool_trace VARCHAR(100000);
