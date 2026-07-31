-- ─────────────────────────────────────────────
-- V1: MAgent-Platform 初始 schema (11 张表)
-- ─────────────────────────────────────────────

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 通用: updated_at 自动维护
CREATE OR REPLACE FUNCTION trg_set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ===== 1. agents =====
CREATE TABLE agents (
    id              VARCHAR(64) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    name            VARCHAR(100) NOT NULL,
    description     TEXT,
    dify_base_url   VARCHAR(255),
    dify_app_id     VARCHAR(100),
    dify_api_key    VARCHAR(255),           -- AES
    skills          JSONB        DEFAULT '[]',
    capabilities    JSONB        DEFAULT '{}',
    approval_skills JSONB        DEFAULT '[]',
    status          VARCHAR(20)  DEFAULT 'active',
    last_health_at  TIMESTAMP,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted         VARCHAR(1) DEFAULT '0'
);
CREATE INDEX idx_agents_status       ON agents(status) WHERE deleted = '0';
CREATE TRIGGER agents_set_updated_at  BEFORE UPDATE ON agents
    FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();

-- ===== 2. feishu_bots =====
CREATE TABLE feishu_bots (
    id                  VARCHAR(64) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    name                VARCHAR(100) NOT NULL,
    app_id              VARCHAR(100) NOT NULL,
    app_secret          VARCHAR(255),         -- AES
    verification_token  VARCHAR(255),         -- AES
    encrypt_key         VARCHAR(255),         -- AES
    webhook_url         VARCHAR(255),
    bound_agent_id      VARCHAR(64) REFERENCES agents(id),
    status              VARCHAR(20)  DEFAULT 'active',
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted             VARCHAR(1)  DEFAULT '0'
);
CREATE INDEX idx_feishu_bots_status ON feishu_bots(status) WHERE deleted = '0';
CREATE TRIGGER feishu_bots_set_updated_at BEFORE UPDATE ON feishu_bots
    FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();

-- ===== 3. orchestration_rules =====
CREATE TABLE orchestration_rules (
    id              VARCHAR(64) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    name            VARCHAR(100) NOT NULL,
    description     TEXT,
    trigger_type    VARCHAR(30)  NOT NULL,   -- keyword/regex/intent/manual/all
    trigger_config  JSONB        DEFAULT '{}',
    execution_mode  VARCHAR(30)  DEFAULT 'sequential',
    agent_chain     JSONB        DEFAULT '[]',
    fallback_agent_id VARCHAR(64) REFERENCES agents(id),
    priority        INTEGER      DEFAULT 100,
    enabled         BOOLEAN      DEFAULT TRUE,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted         VARCHAR(1)  DEFAULT '0'
);
CREATE INDEX idx_rules_enabled ON orchestration_rules(enabled, priority) WHERE deleted = '0';
CREATE TRIGGER orchestration_rules_set_updated_at BEFORE UPDATE ON orchestration_rules
    FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();

-- ===== 4. approval_policies =====
CREATE TABLE approval_policies (
    id                  VARCHAR(64) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    name                VARCHAR(100) NOT NULL,
    strategy            VARCHAR(30)  NOT NULL, -- auto/notify/require_one/require_quorum/require_role
    quorum              INTEGER,
    required_role       VARCHAR(50),
    timeout_seconds     INTEGER      DEFAULT 1800,
    timeout_action      VARCHAR(20)  DEFAULT 'auto_reject',
    escalation_channel  JSONB        DEFAULT '{}',
    applies_to          JSONB        DEFAULT '{}',
    enabled             BOOLEAN      DEFAULT TRUE,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted             VARCHAR(1)  DEFAULT '0'
);
CREATE TRIGGER approval_policies_set_updated_at BEFORE UPDATE ON approval_policies
    FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();

-- ===== 5. conversations =====
CREATE TABLE conversations (
    id                VARCHAR(64) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    source            VARCHAR(20) NOT NULL, -- feishu/web/api
    external_chat_id  VARCHAR(100),
    external_user_id  VARCHAR(100),
    a2a_context_id    VARCHAR(100),
    status            VARCHAR(20) DEFAULT 'active',
    closed_at         TIMESTAMP,
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted           VARCHAR(1) DEFAULT '0'
);
CREATE INDEX idx_conversations_context ON conversations(a2a_context_id) WHERE deleted = '0';
CREATE INDEX idx_conversations_source  ON conversations(source) WHERE deleted = '0';
CREATE TRIGGER conversations_set_updated_at BEFORE UPDATE ON conversations
    FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();

-- ===== 6. messages =====
CREATE TABLE messages (
    id              VARCHAR(64) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    conversation_id VARCHAR(64) NOT NULL REFERENCES conversations(id),
    role            VARCHAR(20) NOT NULL,   -- user/agent/orchestrator/system
    agent_id        VARCHAR(64) REFERENCES agents(id),
    parts           JSONB       DEFAULT '[]',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_messages_conversation ON messages(conversation_id, created_at);

-- ===== 7. a2a_tasks =====
CREATE TABLE a2a_tasks (
    id              VARCHAR(100) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    conversation_id VARCHAR(64) REFERENCES conversations(id),
    context_id      VARCHAR(100),
    assigned_agent_id VARCHAR(64) REFERENCES agents(id),
    parent_task_id  VARCHAR(100),
    status          VARCHAR(30) DEFAULT 'submitted',
    message_history JSONB       DEFAULT '[]',
    artifacts       JSONB       DEFAULT '[]',
    error_detail    TEXT,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at    TIMESTAMP
);
CREATE INDEX idx_tasks_status       ON a2a_tasks(status);
CREATE INDEX idx_tasks_conversation ON a2a_tasks(conversation_id);
CREATE INDEX idx_tasks_context      ON a2a_tasks(context_id);
CREATE INDEX idx_tasks_agent        ON a2a_tasks(assigned_agent_id);
CREATE TRIGGER a2a_tasks_set_updated_at BEFORE UPDATE ON a2a_tasks
    FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();

-- ===== 8. approvals =====
CREATE TABLE approvals (
    id              VARCHAR(64) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    task_id         VARCHAR(100) NOT NULL REFERENCES a2a_tasks(id),
    policy_id       VARCHAR(64) REFERENCES approval_policies(id),
    requested_by    VARCHAR(100),
    skill_name      VARCHAR(100),
    payload         JSONB       DEFAULT '{}',
    status          VARCHAR(20) DEFAULT 'pending', -- pending/approved/rejected/expired
    decision_by     VARCHAR(100),
    decision_at     TIMESTAMP,
    decision_channel VARCHAR(20),                  -- feishu/web/timeout
    comment         TEXT,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_approvals_status ON approvals(status);
CREATE INDEX idx_approvals_task   ON approvals(task_id);

-- ===== 9. system_settings =====
CREATE TABLE system_settings (
    id          VARCHAR(64) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    key         VARCHAR(100) NOT NULL UNIQUE,
    value       JSONB,
    description TEXT,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TRIGGER system_settings_set_updated_at BEFORE UPDATE ON system_settings
    FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();

-- ===== 10. audit_logs =====
CREATE TABLE audit_logs (
    id          VARCHAR(64) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    actor_id    VARCHAR(100),
    action      VARCHAR(50),
    entity_type VARCHAR(50),
    entity_id   VARCHAR(100),
    details     JSONB,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_audit_logs_action ON audit_logs(action, created_at);
CREATE INDEX idx_audit_logs_entity ON audit_logs(entity_type, entity_id);

-- ===== 11. admins =====
CREATE TABLE admins (
    id              VARCHAR(64) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    username        VARCHAR(50)  NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    role            VARCHAR(20)  DEFAULT 'super_admin',
    feishu_user_id  VARCHAR(100),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);