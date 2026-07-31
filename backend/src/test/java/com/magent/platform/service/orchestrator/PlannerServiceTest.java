package com.magent.platform.service.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.magent.platform.dto.orchestrator.ExecutionPlan;
import com.magent.platform.entity.Agent;
import com.magent.platform.entity.OrchestrationRule;
import com.magent.platform.mapper.AgentMapper;
import com.magent.platform.mapper.OrchestrationRuleMapper;
import com.magent.platform.service.llm.LLMService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlannerServiceTest {

    @Mock private AgentMapper agentMapper;
    @Mock private OrchestrationRuleMapper ruleMapper;
    @Mock private LLMService llmService;
    private final ObjectMapper om = new ObjectMapper();

    @InjectMocks private PlannerService planner;

    private Agent agent1;
    private Agent agent2;

    @BeforeEach
    void setup() {
        planner = new PlannerService(agentMapper, ruleMapper, llmService, om);

        agent1 = new Agent();
        agent1.setId("agent-001");
        agent1.setName("研究员");
        agent1.setStatus("active");

        agent2 = new Agent();
        agent2.setId("agent-002");
        agent2.setName("撰稿人");
        agent2.setStatus("active");
    }

    @Test
    void keywordRuleMatch_returnsPlanFromRule() {
        OrchestrationRule rule = new OrchestrationRule();
        rule.setName("分析任务");
        rule.setTriggerType("keyword");
        rule.setTriggerConfig("{\"keywords\":[\"分析\",\"调研\"]}");
        rule.setExecutionMode("sequential");
        rule.setAgentChain("[{\"agentId\":\"agent-001\",\"role\":\"研究员\",\"inputFrom\":\"user\"}]");

        when(ruleMapper.selectList(any())).thenReturn(List.of(rule));
        when(agentMapper.selectList(any())).thenReturn(List.of(agent1, agent2));

        ExecutionPlan plan = planner.plan("帮我分析下竞品");

        assertThat(plan.executionMode()).isEqualTo("sequential");
        assertThat(plan.stages()).hasSize(1);
        assertThat(plan.stages().get(0).agentId()).isEqualTo("agent-001");
        assertThat(plan.stages().get(0).agentName()).isEqualTo("研究员");
        assertThat(plan.reasoning()).contains("分析任务");
    }

    @Test
    void regexRuleMatch_returnsPlanFromRule() {
        OrchestrationRule rule = new OrchestrationRule();
        rule.setName("价格查询");
        rule.setTriggerType("regex");
        rule.setTriggerConfig("{\"regex\":\"价格\"}");
        rule.setExecutionMode("router");
        rule.setAgentChain("[{\"agentId\":\"agent-002\",\"role\":\"报价\",\"inputFrom\":\"user\"}]");

        when(ruleMapper.selectList(any())).thenReturn(List.of(rule));
        when(agentMapper.selectList(any())).thenReturn(List.of(agent1, agent2));

        ExecutionPlan plan = planner.plan("这个产品的价格是多少");

        assertThat(plan.executionMode()).isEqualTo("router");
        assertThat(plan.stages()).hasSize(1);
        assertThat(plan.stages().get(0).agentId()).isEqualTo("agent-002");
    }

    @Test
    void noRuleMatch_fallsBackToLLM() {
        ExecutionPlan llmPlan = ExecutionPlan.of("sequential",
            List.of(new com.magent.platform.dto.orchestrator.Stage("agent-001", "研究员", "user", "research", 0)),
            "LLM decided");

        when(ruleMapper.selectList(any())).thenReturn(List.of());
        when(agentMapper.selectList(any())).thenReturn(List.of(agent1));
        when(llmService.parseIntent(anyString(), anyList(), anyList())).thenReturn(llmPlan);

        ExecutionPlan plan = planner.plan("帮我做点事情");

        assertThat(plan.executionMode()).isEqualTo("sequential");
        assertThat(plan.reasoning()).isEqualTo("LLM decided");
    }

    @Test
    void allTriggerType_alwaysMatches() {
        OrchestrationRule rule = new OrchestrationRule();
        rule.setName("默认规则");
        rule.setTriggerType("all");
        rule.setTriggerConfig("{}");
        rule.setExecutionMode("parallel");
        rule.setAgentChain("[{\"agentId\":\"agent-001\",\"role\":\"a\",\"inputFrom\":\"user\"},{\"agentId\":\"agent-002\",\"role\":\"b\",\"inputFrom\":\"user\"}]");

        when(ruleMapper.selectList(any())).thenReturn(List.of(rule));
        when(agentMapper.selectList(any())).thenReturn(List.of(agent1, agent2));

        ExecutionPlan plan = planner.plan("任何消息");

        assertThat(plan.executionMode()).isEqualTo("parallel");
        assertThat(plan.stages()).hasSize(2);
    }
}