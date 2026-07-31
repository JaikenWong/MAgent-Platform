package com.magent.platform.service.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.magent.platform.entity.AuditLog;
import com.magent.platform.mapper.AuditLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 审计日志服务: 记录关键操作 (CRUD / 审批决策 / 配置变更).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogMapper auditLogMapper;
    private final ObjectMapper om;

    public void log(String actorId, String action, String entityType, String entityId, Map<String, Object> details) {
        try {
            AuditLog entry = new AuditLog();
            entry.setActorId(actorId);
            entry.setAction(action);
            entry.setEntityType(entityType);
            entry.setEntityId(entityId);
            entry.setDetails(details != null ? om.writeValueAsString(details) : null);
            entry.setCreatedAt(LocalDateTime.now());
            auditLogMapper.insert(entry);
        } catch (Exception e) {
            log.warn("failed to write audit log: action={} entity={}/{}", action, entityType, entityId, e);
        }
    }

    public void log(String actorId, String action, String entityType, String entityId) {
        log(actorId, action, entityType, entityId, null);
    }
}