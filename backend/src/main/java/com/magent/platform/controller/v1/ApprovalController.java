package com.magent.platform.controller.v1;

import com.magent.platform.common.R;
import com.magent.platform.dto.PageQuery;
import com.magent.platform.dto.PageResult;
import com.magent.platform.entity.Approval;
import com.magent.platform.mapper.ApprovalMapper;
import com.magent.platform.service.approval.ApprovalEngine;
import com.magent.platform.service.approval.ApprovalNotifier;
import com.magent.platform.service.audit.AuditService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/approvals")
public class ApprovalController {

    private final ApprovalMapper approvalMapper;
    private final ApprovalEngine engine;
    private final ApprovalNotifier notifier;
    private final AuditService auditService;

    public ApprovalController(ApprovalMapper approvalMapper, ApprovalEngine engine,
            ApprovalNotifier notifier, AuditService auditService) {
        this.approvalMapper = approvalMapper;
        this.engine = engine;
        this.notifier = notifier;
        this.auditService = auditService;
    }

    @GetMapping
    public R<PageResult<Approval>> list(PageQuery q,
                                        @RequestParam(required = false) String status) {
        Page<Approval> page = new Page<>(q.safePage(), q.safeSize());
        QueryWrapper<Approval> w = new QueryWrapper<>();
        if (status != null && !status.isEmpty()) w.eq("status", status);
        w.orderByDesc("created_at");
        approvalMapper.selectPage(page, w);
        return R.ok(PageResult.of(page.getRecords(), page.getTotal(),
                (int) page.getCurrent(), (int) page.getSize()));
    }

    @GetMapping("/pending/count")
    public R<Integer> pendingCount() {
        return R.ok(engine.pendingCount());
    }

    @GetMapping("/{id}")
    public R<Approval> get(@PathVariable String id) {
        return R.ok(approvalMapper.selectById(id));
    }

    @PostMapping("/{id}/decide")
    public R<Approval> decide(@PathVariable String id,
                              @RequestBody Map<String, String> body) {
        String decision = body.get("decision"); // approved | rejected
        String comment = body.get("comment");
        String actor = body.getOrDefault("actor", "unknown");
        engine.decide(id, "approved".equals(decision), comment, actor, "web");
        Approval a = approvalMapper.selectById(id);
        auditService.log(actor, "approve".equals(decision) ? "approval_approve" : "approval_reject",
            "approval", id, Map.of("taskId", a != null ? a.getTaskId() : "", "comment", comment == null ? "" : comment));
        notifier.pushPendingCount(engine.pendingCount());
        return R.ok(a);
    }
}