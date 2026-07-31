package com.magent.platform.controller.v1;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.magent.platform.common.R;
import com.magent.platform.entity.SystemSetting;
import com.magent.platform.mapper.SystemSettingMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
public class SettingController {

    private final SystemSettingMapper mapper;
    private final ObjectMapper objectMapper;

    @GetMapping
    public R<List<SystemSetting>> list() {
        return R.ok(mapper.selectList(null));
    }

    @PutMapping
    public R<Void> bulk(@RequestBody Map<String, Object> updates) {
        updates.forEach((k, v) -> {
            SystemSetting s = mapper.selectOne(new QueryWrapper<SystemSetting>().eq("key", k));
            if (s == null) return;
            try {
                s.setValue(objectMapper.writeValueAsString(v));
            } catch (Exception e) {
                s.setValue(String.valueOf(v));
            }
            mapper.updateById(s);
        });
        return R.ok();
    }
}