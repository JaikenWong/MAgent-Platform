package com.magent.platform.controller.v1;

import com.magent.platform.common.R;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final JdbcTemplate jdbc;

    @GetMapping("/stats")
    public R<Map<String, Object>> stats() {
        Map<String, Object> m = new HashMap<>();
        m.put("agentCount",      count("SELECT count(*) FROM agents WHERE status='active'"));
        m.put("taskCountToday",  count("SELECT count(*) FROM a2a_tasks WHERE created_at >= CURRENT_DATE"));
        m.put("pendingApprovals", count("SELECT count(*) FROM approvals WHERE status='pending'"));
        m.put("feishuMessageCount", 0L);
        m.put("taskDistribution", Map.of(
            "completed", count("SELECT count(*) FROM a2a_tasks WHERE status='completed'"),
            "working", count("SELECT count(*) FROM a2a_tasks WHERE status='working'"),
            "failed", count("SELECT count(*) FROM a2a_tasks WHERE status='failed'"),
            "inputRequired", count("SELECT count(*) FROM a2a_tasks WHERE status='input_required'")
        ));
        m.put("recentConversations", jdbc.queryForList(
            "SELECT id, source, status, created_at as createdAt FROM conversations ORDER BY created_at DESC LIMIT 10"));
        return R.ok(m);
    }

    private long count(String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0 : n;
    }
}