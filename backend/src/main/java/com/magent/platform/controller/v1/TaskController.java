package com.magent.platform.controller.v1;

import com.magent.platform.common.R;
import com.magent.platform.dto.PageQuery;
import com.magent.platform.dto.PageResult;
import com.magent.platform.entity.Task;
import com.magent.platform.mapper.TaskMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskMapper taskMapper;

    @GetMapping
    public R<PageResult<Task>> list(PageQuery q) {
        Page<Task> page = new Page<>(q.safePage(), q.safeSize());
        taskMapper.selectPage(page, null);
        return R.ok(PageResult.of(page.getRecords(), page.getTotal(),
                (int) page.getCurrent(), (int) page.getSize()));
    }

    @GetMapping("/{id}")
    public R<Task> get(@PathVariable String id) {
        return R.ok(taskMapper.selectById(id));
    }

    @PostMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable String id) {
        Task t = taskMapper.selectById(id);
        if (t != null) {
            t.setStatus("canceled");
            taskMapper.updateById(t);
        }
        return R.ok();
    }
}