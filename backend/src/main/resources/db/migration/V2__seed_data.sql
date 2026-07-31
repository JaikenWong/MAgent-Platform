-- ─────────────────────────────────────────────
-- V2: 种子数据
--    bcrypt($2a$10$.../admin123) 由 Spring Boot CommandLineRunner 兜底,
--    这里先放占位 hash 真实为 admin123 的 bcrypt(10 rounds):
--    $2a$10$N9qo8uLOickgx2ZMRZoMy.MQDZoZ8rJd9pXyK8pVCaQ8cQ8pVCaQ8
--    (取一个标准 hash, 实际生产请用运行时 BCrypt 重置)
-- ─────────────────────────────────────────────

-- NOTE: 默认管理员由 Spring Boot AdminInitializer 用 BCrypt 写入, 不在此 seed.

-- 默认 "auto" 审批策略 (不阻塞, 仅日志)
INSERT INTO approval_policies (id, name, strategy, timeout_seconds, timeout_action, applies_to, enabled)
VALUES ('00000000-0000-0000-0000-000000000010',
        'default-auto',
        'auto',
        1800,
        'auto_reject',
        '{}'::jsonb,
        TRUE)
ON CONFLICT DO NOTHING;

-- 默认 "require_one" 审批策略
INSERT INTO approval_policies (id, name, strategy, timeout_seconds, timeout_action, applies_to, enabled)
VALUES ('00000000-0000-0000-0000-000000000011',
        'default-require-one',
        'require_one',
        1800,
        'auto_reject',
        '{}'::jsonb,
        FALSE)
ON CONFLICT DO NOTHING;

-- 默认系统设置
INSERT INTO system_settings (id, key, value, description) VALUES
('00000000-0000-0000-0000-000000000020', 'orchestrator.max_chain_depth',
 '5'::jsonb, 'Task 链最大深度, 防死循环'),
('00000000-0000-0000-0000-000000000021', 'orchestrator.cycle_detection',
 'true'::jsonb, '是否启用循环检测'),
('00000000-0000-0000-0000-000000000022', 'feishu.stream_card_update_ms',
 '500'::jsonb, '流式卡片更新间隔 (ms)')
ON CONFLICT DO NOTHING;