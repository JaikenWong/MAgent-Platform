-- ===== V3: 飞书长连接开关 =====
ALTER TABLE feishu_bots
    ADD COLUMN long_connection_enabled BOOLEAN DEFAULT FALSE;
