-- =====================================================================
-- V1: initial schema for the Flat Meal Manager
-- Flyway runs this once, in order, and records it in flyway_schema_history.
-- NEVER edit this file after it has run; add V2__..., V3__... instead.
--
-- Conventions
--   * Money:       DECIMAL(12,2)   (never FLOAT/DOUBLE)
--   * Meal values: DECIMAL(4,2)
--   * Balances:    positive = member owes the flat, negative = member has credit
--   * Financial rows are never deleted: they get status = 'VOIDED' + a reason.
-- =====================================================================

-- ---------------------------------------------------------------------
-- People and logins
-- ---------------------------------------------------------------------
CREATE TABLE members (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    status      ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    joined_on   DATE NOT NULL,
    left_on     DATE NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_members_name UNIQUE (name),
    CONSTRAINT ck_members_dates CHECK (left_on IS NULL OR left_on >= joined_on)
);

CREATE TABLE users (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    username       VARCHAR(50)  NOT NULL,
    password_hash  VARCHAR(100) NOT NULL,          -- BCrypt hash, never plain text
    role           ENUM('MANAGER','MEMBER') NOT NULL,
    member_id      BIGINT NULL,                    -- which flat member this login belongs to
    enabled        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_member   UNIQUE (member_id),
    CONSTRAINT fk_users_member   FOREIGN KEY (member_id) REFERENCES members(id)
);

-- ---------------------------------------------------------------------
-- Months
-- ---------------------------------------------------------------------
CREATE TABLE months (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    year           SMALLINT NOT NULL,
    month          TINYINT  NOT NULL,
    status         ENUM('OPEN','CLOSED') NOT NULL DEFAULT 'OPEN',
    -- The three values below are filled (frozen) when the month is closed.
    total_meals    DECIMAL(8,2)  NULL,
    total_expense  DECIMAL(12,2) NULL,
    meal_rate      DECIMAL(14,6) NULL,
    closed_at      TIMESTAMP NULL,
    closed_by      BIGINT NULL,
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_months_year_month UNIQUE (year, month),
    CONSTRAINT ck_months_month CHECK (month BETWEEN 1 AND 12),
    CONSTRAINT ck_months_year  CHECK (year BETWEEN 2000 AND 2100),
    CONSTRAINT fk_months_closed_by FOREIGN KEY (closed_by) REFERENCES users(id)
);

-- Who appears in a month's meal grid. Frozen history: deactivating a member
-- later never removes them from months they were part of.
CREATE TABLE month_members (
    month_id   BIGINT NOT NULL,
    member_id  BIGINT NOT NULL,
    PRIMARY KEY (month_id, member_id),
    CONSTRAINT fk_mm_month  FOREIGN KEY (month_id)  REFERENCES months(id),
    CONSTRAINT fk_mm_member FOREIGN KEY (member_id) REFERENCES members(id)
);

-- ---------------------------------------------------------------------
-- Daily meals
-- ---------------------------------------------------------------------
CREATE TABLE meal_entries (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    month_id    BIGINT NOT NULL,
    member_id   BIGINT NOT NULL,
    meal_date   DATE NOT NULL,
    meals       DECIMAL(4,2) NOT NULL,
    updated_by  BIGINT NULL,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_meal_member_date UNIQUE (member_id, meal_date),
    -- Quarter-meal steps from 0 to 2 (0, 0.25, 0.5, ... 2). The UI offers the
    -- named presets; the database guarantees nothing else can sneak in.
    CONSTRAINT ck_meal_value CHECK (meals >= 0 AND meals <= 2 AND MOD(meals * 100, 25) = 0),
    CONSTRAINT fk_meal_month  FOREIGN KEY (month_id)   REFERENCES months(id),
    CONSTRAINT fk_meal_member FOREIGN KEY (member_id)  REFERENCES members(id),
    CONSTRAINT fk_meal_user   FOREIGN KEY (updated_by) REFERENCES users(id)
);
CREATE INDEX ix_meal_month ON meal_entries(month_id);

-- ---------------------------------------------------------------------
-- Expenses
--   funded_by = FLAT_CASH     -> paid from flat cash held by holder_member_id
--   funded_by = MEMBER_POCKET -> holder_member_id paid with their own money,
--                                so that member's account is credited
-- ---------------------------------------------------------------------
CREATE TABLE expenses (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    month_id          BIGINT NOT NULL,
    expense_date      DATE NOT NULL,
    category          VARCHAR(50)  NOT NULL,
    description       VARCHAR(255) NULL,
    amount            DECIMAL(12,2) NOT NULL,
    in_meal_rate      BOOLEAN NOT NULL DEFAULT TRUE,
    funded_by         ENUM('FLAT_CASH','MEMBER_POCKET') NOT NULL,
    holder_member_id  BIGINT NOT NULL,
    status            ENUM('ACTIVE','VOIDED') NOT NULL DEFAULT 'ACTIVE',
    void_reason       VARCHAR(255) NULL,
    created_by        BIGINT NULL,
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_expense_amount CHECK (amount > 0),
    CONSTRAINT ck_expense_void   CHECK (status = 'ACTIVE' OR void_reason IS NOT NULL),
    CONSTRAINT fk_expense_month   FOREIGN KEY (month_id)         REFERENCES months(id),
    CONSTRAINT fk_expense_holder  FOREIGN KEY (holder_member_id) REFERENCES members(id),
    CONSTRAINT fk_expense_creator FOREIGN KEY (created_by)       REFERENCES users(id)
);
CREATE INDEX ix_expense_month ON expenses(month_id);

-- ---------------------------------------------------------------------
-- Member payments (one row = one real event: it lowers the payer's balance
-- AND adds cash to the receiver's holdings). REFUND = flat pays a member back.
-- ---------------------------------------------------------------------
CREATE TABLE payments (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    month_id               BIGINT NOT NULL,
    member_id              BIGINT NOT NULL,
    received_by_member_id  BIGINT NOT NULL,
    amount                 DECIMAL(12,2) NOT NULL,
    type                   ENUM('PAYMENT','REFUND') NOT NULL DEFAULT 'PAYMENT',
    paid_on                DATE NOT NULL,
    note                   VARCHAR(255) NULL,
    status                 ENUM('ACTIVE','VOIDED') NOT NULL DEFAULT 'ACTIVE',
    void_reason            VARCHAR(255) NULL,
    created_by             BIGINT NULL,
    created_at             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_payment_amount CHECK (amount > 0),
    CONSTRAINT ck_payment_void   CHECK (status = 'ACTIVE' OR void_reason IS NOT NULL),
    CONSTRAINT fk_payment_month    FOREIGN KEY (month_id)              REFERENCES months(id),
    CONSTRAINT fk_payment_member   FOREIGN KEY (member_id)             REFERENCES members(id),
    CONSTRAINT fk_payment_receiver FOREIGN KEY (received_by_member_id) REFERENCES members(id),
    CONSTRAINT fk_payment_creator  FOREIGN KEY (created_by)            REFERENCES users(id)
);
CREATE INDEX ix_payment_month ON payments(month_id);

-- ---------------------------------------------------------------------
-- Authorised balance adjustments (signed: + increases what the member owes)
-- OPENING is used once, for balances copied from the old spreadsheet.
-- ---------------------------------------------------------------------
CREATE TABLE adjustments (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    month_id     BIGINT NOT NULL,
    member_id    BIGINT NOT NULL,
    amount       DECIMAL(12,2) NOT NULL,
    type         ENUM('OPENING','CORRECTION','OTHER') NOT NULL,
    reason       VARCHAR(255) NOT NULL,
    status       ENUM('ACTIVE','VOIDED') NOT NULL DEFAULT 'ACTIVE',
    void_reason  VARCHAR(255) NULL,
    created_by   BIGINT NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_adjustment_nonzero CHECK (amount <> 0),
    CONSTRAINT ck_adjustment_void    CHECK (status = 'ACTIVE' OR void_reason IS NOT NULL),
    CONSTRAINT fk_adj_month   FOREIGN KEY (month_id)   REFERENCES months(id),
    CONSTRAINT fk_adj_member  FOREIGN KEY (member_id)  REFERENCES members(id),
    CONSTRAINT fk_adj_creator FOREIGN KEY (created_by) REFERENCES users(id)
);
CREATE INDEX ix_adjustment_month ON adjustments(month_id);

-- ---------------------------------------------------------------------
-- Cash custody (where the flat's physical money is). None of these
-- tables touch anyone's personal balance.
-- ---------------------------------------------------------------------
CREATE TABLE cash_transfers (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    month_id         BIGINT NOT NULL,
    from_member_id   BIGINT NOT NULL,
    to_member_id     BIGINT NOT NULL,
    amount           DECIMAL(12,2) NOT NULL,
    transferred_on   DATE NOT NULL,
    note             VARCHAR(255) NULL,
    status           ENUM('ACTIVE','VOIDED') NOT NULL DEFAULT 'ACTIVE',
    void_reason      VARCHAR(255) NULL,
    created_by       BIGINT NULL,
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_transfer_amount CHECK (amount > 0),
    CONSTRAINT ck_transfer_people CHECK (from_member_id <> to_member_id),
    CONSTRAINT ck_transfer_void   CHECK (status = 'ACTIVE' OR void_reason IS NOT NULL),
    CONSTRAINT fk_transfer_month FOREIGN KEY (month_id)       REFERENCES months(id),
    CONSTRAINT fk_transfer_from  FOREIGN KEY (from_member_id) REFERENCES members(id),
    CONSTRAINT fk_transfer_to    FOREIGN KEY (to_member_id)   REFERENCES members(id),
    CONSTRAINT fk_transfer_user  FOREIGN KEY (created_by)     REFERENCES users(id)
);

-- Money from outside the members' meal payments (e.g. selling old gas cylinder)
CREATE TABLE cash_receipts (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    month_id          BIGINT NOT NULL,
    holder_member_id  BIGINT NOT NULL,
    amount            DECIMAL(12,2) NOT NULL,
    source            VARCHAR(255) NOT NULL,
    received_on       DATE NOT NULL,
    status            ENUM('ACTIVE','VOIDED') NOT NULL DEFAULT 'ACTIVE',
    void_reason       VARCHAR(255) NULL,
    created_by        BIGINT NULL,
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_receipt_amount CHECK (amount > 0),
    CONSTRAINT ck_receipt_void   CHECK (status = 'ACTIVE' OR void_reason IS NOT NULL),
    CONSTRAINT fk_receipt_month  FOREIGN KEY (month_id)         REFERENCES months(id),
    CONSTRAINT fk_receipt_holder FOREIGN KEY (holder_member_id) REFERENCES members(id),
    CONSTRAINT fk_receipt_user   FOREIGN KEY (created_by)       REFERENCES users(id)
);

-- Physical counts of cash in hand, compared against the recorded figures
CREATE TABLE cash_counts (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    month_id          BIGINT NOT NULL,
    holder_member_id  BIGINT NOT NULL,
    counted_amount    DECIMAL(12,2) NOT NULL,
    counted_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    note              VARCHAR(255) NULL,
    created_by        BIGINT NULL,
    CONSTRAINT ck_count_amount CHECK (counted_amount >= 0),
    CONSTRAINT fk_count_month  FOREIGN KEY (month_id)         REFERENCES months(id),
    CONSTRAINT fk_count_holder FOREIGN KEY (holder_member_id) REFERENCES members(id),
    CONSTRAINT fk_count_user   FOREIGN KEY (created_by)       REFERENCES users(id)
);

-- Explicit, explained corrections to a holder's cash (shortage / excess / opening cash)
CREATE TABLE cash_adjustments (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    month_id          BIGINT NOT NULL,
    holder_member_id  BIGINT NOT NULL,
    amount            DECIMAL(12,2) NOT NULL,
    type              ENUM('OPENING','SHORTAGE','EXCESS','OTHER') NOT NULL,
    reason            VARCHAR(255) NOT NULL,
    status            ENUM('ACTIVE','VOIDED') NOT NULL DEFAULT 'ACTIVE',
    void_reason       VARCHAR(255) NULL,
    created_by        BIGINT NULL,
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_cashadj_nonzero CHECK (amount <> 0),
    CONSTRAINT ck_cashadj_void    CHECK (status = 'ACTIVE' OR void_reason IS NOT NULL),
    CONSTRAINT fk_cashadj_month  FOREIGN KEY (month_id)         REFERENCES months(id),
    CONSTRAINT fk_cashadj_holder FOREIGN KEY (holder_member_id) REFERENCES members(id),
    CONSTRAINT fk_cashadj_user   FOREIGN KEY (created_by)       REFERENCES users(id)
);

-- ---------------------------------------------------------------------
-- Snapshots written in one transaction when a month is closed.
-- Next month's opening balance/cash is read from here (carry-forward
-- without copying any transactions).
-- ---------------------------------------------------------------------
CREATE TABLE month_member_summary (
    month_id           BIGINT NOT NULL,
    member_id          BIGINT NOT NULL,
    opening_balance    DECIMAL(12,2) NOT NULL,
    meal_count         DECIMAL(8,2)  NOT NULL,
    meal_cost          DECIMAL(12,2) NOT NULL,
    payments_total     DECIMAL(12,2) NOT NULL,   -- payments minus refunds
    adjustments_total  DECIMAL(12,2) NOT NULL,
    pocket_credit      DECIMAL(12,2) NOT NULL,
    closing_balance    DECIMAL(12,2) NOT NULL,
    PRIMARY KEY (month_id, member_id),
    CONSTRAINT fk_mms_month  FOREIGN KEY (month_id)  REFERENCES months(id),
    CONSTRAINT fk_mms_member FOREIGN KEY (member_id) REFERENCES members(id)
);

CREATE TABLE month_cash_summary (
    month_id          BIGINT NOT NULL,
    holder_member_id  BIGINT NOT NULL,
    opening_cash      DECIMAL(12,2) NOT NULL,
    cash_in           DECIMAL(12,2) NOT NULL,
    cash_out          DECIMAL(12,2) NOT NULL,
    closing_cash      DECIMAL(12,2) NOT NULL,
    PRIMARY KEY (month_id, holder_member_id),
    CONSTRAINT fk_mcs_month  FOREIGN KEY (month_id)         REFERENCES months(id),
    CONSTRAINT fk_mcs_holder FOREIGN KEY (holder_member_id) REFERENCES members(id)
);

-- ---------------------------------------------------------------------
-- Audit trail of important actions (append-only)
-- ---------------------------------------------------------------------
CREATE TABLE audit_log (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    actor_user_id  BIGINT NULL,
    entity_type    VARCHAR(50) NOT NULL,     -- e.g. 'MEAL_ENTRY', 'EXPENSE', 'MONTH'
    entity_id      BIGINT NOT NULL,
    action         ENUM('CREATE','UPDATE','VOID','CLOSE','REOPEN') NOT NULL,
    old_value      JSON NULL,
    new_value      JSON NULL,
    reason         VARCHAR(255) NULL,
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_user FOREIGN KEY (actor_user_id) REFERENCES users(id)
);
CREATE INDEX ix_audit_entity ON audit_log(entity_type, entity_id);
CREATE INDEX ix_audit_time   ON audit_log(created_at);
