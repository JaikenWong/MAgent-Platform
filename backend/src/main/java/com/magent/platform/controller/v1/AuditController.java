package com.magent.platform.controller.v1;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.magent.platform.common.R;
import com.magent.platform.entity.AuditLog;
import com.magent.platform.dto.PageQuery;
import com.magent.platform.dto.PageResult;
import com.magent.platform.mapper.AuditLogMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditLogMapper mapper;

    @GetMapping
    public R<PageResult<AuditLog>> list(PageQuery q,
                                        @RequestParam(required = false) String action) {
        Page<AuditLog> page = new Page<>(q.safePage(), q.safeSize());
        QueryWrapper<AuditLog> w = new QueryWrapper<AuditLog>().orderByDesc("created_at");
        if (action != null && !action.isEmpty()) w.eq("action", action);
        mapper.selectPage(page, w);
        return R.ok(PageResult.of(page.getRecords(), page.getTotal(),
                (int) page.getCurrent(), (int) page.getSize()));
    }
}