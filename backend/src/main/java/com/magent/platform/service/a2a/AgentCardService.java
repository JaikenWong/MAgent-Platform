package com.magent.platform.service.a2a;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.magent.platform.common.BizException;
import com.magent.platform.common.CryptoUtil;
import com.magent.platform.dto.a2a.AgentCard;
import com.magent.platform.dto.a2a.AgentCard.AgentCapabilities;
import com.magent.platform.dto.a2a.AgentCard.AgentSkill;
import com.magent.platform.entity.Agent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Agent Card 生成: 从 DB Agent 实体构建 A2A AgentCard.
 *  基础信息来自 Agent; skills/capabilities 每个实体 JSON 字段自定义.
 *  统一字段加密: difyApiKey 是 AES 密文 — 这里不返回任何敏感字段.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentCardService {

    private final ObjectMapper om;

    public AgentCard build(Agent agent, String publicBaseUrl) {
        List<AgentSkill> skills = parseSkills(agent.getSkills());
        AgentCapabilities caps = parseCapabilities(agent.getCapabilities());

        String url = publicBaseUrl.replaceAll("/$", "")
                + "/a2a/" + URLEncoder.encode(agent.getId(), StandardCharsets.UTF_8);

        return new AgentCard(
            agent.getName(),
            agent.getDescription() == null ? "" : agent.getDescription(),
            "0.1.0",
            "1.0",
            url,
            caps,
            skills,
            List.of("text"),
            List.of("text")
        );
    }

    public AgentCard fetch(String agentId, String publicBaseUrl) {
        throwIfEncrypted(agentId);
        return null;
    }

    private List<AgentSkill> parseSkills(String json) {
        if (json == null || json.isBlank()) return List.of(
            new AgentSkill("default", "general", "默认处理用户输入",
                List.of("general"), List.of(), List.of("text"), List.of("text")));
        try {
            return om.readValue(json, new TypeReference<List<AgentSkill>>() {});
        } catch (Exception e) {
            log.warn("agent skills parse failed: {}", e.getMessage());
            return List.of();
        }
    }

    private AgentCapabilities parseCapabilities(String json) {
        if (json == null || json.isBlank()) {
            return new AgentCapabilities(true, false, false);
        }
        try {
            return om.readValue(json, AgentCapabilities.class);
        } catch (Exception e) {
            log.warn("agent capabilities parse failed: {}", e.getMessage());
            return new AgentCapabilities(true, false, false);
        }
    }

    private void throwIfEncrypted(String id) {
        if (id == null || id.contains(" ")) throw new BizException("invalid id");
    }
}