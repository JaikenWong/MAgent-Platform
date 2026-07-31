package com.magent.platform.controller.v1;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.magent.platform.common.R;
import com.magent.platform.dto.PageQuery;
import com.magent.platform.dto.PageResult;
import com.magent.platform.entity.BaseEntity;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 通用 CRUD 基类. 子类只提供 mapper + 实体类信息.
 * 留出扩展点 (beforeSave / afterSave), Phase 1+ 各域控制器可覆盖.
 */
public abstract class CrudController<E extends BaseEntity, M extends BaseMapper<E>> {

    @Autowired
    protected M mapper;

    protected abstract Class<E> entityClass();

    @GetMapping
    public R<PageResult<E>> list(PageQuery q) {
        Page<E> page = new Page<>(q.safePage(), q.safeSize());
        mapper.selectPage(page, null);
        return R.ok(PageResult.of(page.getRecords(), page.getTotal(), (int) page.getCurrent(), (int) page.getSize()));
    }

    @GetMapping("/{id}")
    public R<E> get(@PathVariable String id) {
        return R.ok(mapper.selectById(id));
    }

    @PostMapping
    public R<E> create(@Valid @RequestBody E entity) {
        mapper.insert(entity);
        return R.ok(entity);
    }

    @PutMapping("/{id}")
    public R<E> update(@PathVariable String id, @RequestBody E entity) {
        entity.setId(id);
        mapper.updateById(entity);
        return R.ok(entity);
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable String id) {
        mapper.deleteById(id);
        return R.ok();
    }

    @GetMapping("/all")
    public R<List<E>> all() {
        return R.ok(mapper.selectList(null));
    }
}