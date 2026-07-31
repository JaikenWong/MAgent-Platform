package com.magent.platform.service.approval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.magent.platform.dto.a2a.TaskState;
import com.magent.platform.entity.Approval;
import com.magent.platform.entity.ApprovalPolicy;
import com.magent.platform.mapper.ApprovalMapper;
import com.magent.platform.mapper.ApprovalPolicyMapper;
import com.magent.platform.service.a2a.TaskManagerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApprovalEngineTest {

    @Mock private ApprovalPolicyMapper policyMapper;
    @Mock private ApprovalMapper approvalMapper;
    @Mock private TaskManagerService taskManagerService;

    private ApprovalEngine engine;
    private final ObjectMapper om = new ObjectMapper();

    @BeforeEach
    void setup() {
        engine = new ApprovalEngine(policyMapper, approvalMapper, taskManagerService, om);
    }

    @Test
    void noPolicy_autoApproves() {
        when(policyMapper.selectList(any())).thenReturn(List.of());

        boolean required = engine.requestApproval("task-1", "agent-1", "send_email", Map.of("to", "boss"));

        assertThat(required).isFalse();
        verify(approvalMapper, never()).insert(any(Approval.class));
        verify(taskManagerService, never()).inputRequired(anyString(), any());
    }

    @Test
    void autoStrategy_autoApproves() {
        ApprovalPolicy policy = new ApprovalPolicy();
        policy.setId("p-1");
        policy.setName("auto policy");
        policy.setStrategy("auto");
        policy.setEnabled(true);

        when(policyMapper.selectList(any())).thenReturn(List.of(policy));

        boolean required = engine.requestApproval("task-1", "agent-1", "send_email", Map.of());

        assertThat(required).isFalse();
        verify(approvalMapper, never()).insert(any(Approval.class));
    }

    @Test
    void requireOneStrategy_suspendsTask() {
        ApprovalPolicy policy = new ApprovalPolicy();
        policy.setId("p-1");
        policy.setName("require_one policy");
        policy.setStrategy("require_one");
        policy.setEnabled(true);

        when(policyMapper.selectList(any())).thenReturn(List.of(policy));

        boolean required = engine.requestApproval("task-1", "agent-1", "send_email", Map.of("to", "boss"));

        assertThat(required).isTrue();

        verify(approvalMapper).insert(Mockito.<Approval>argThat(a ->
            "pending".equals(a.getStatus()) &&
            "task-1".equals(a.getTaskId()) &&
            "send_email".equals(a.getSkillName())));

        verify(taskManagerService).inputRequired(eq("task-1"), any());
    }

    @Test
    void notifyStrategy_doesNotSuspend() {
        ApprovalPolicy policy = new ApprovalPolicy();
        policy.setId("p-1");
        policy.setStrategy("notify");
        policy.setEnabled(true);

        when(policyMapper.selectList(any())).thenReturn(List.of(policy));

        boolean required = engine.requestApproval("task-1", "agent-1", "send_email", Map.of());

        assertThat(required).isFalse();
        verify(taskManagerService, never()).inputRequired(anyString(), any());
    }

    @Test
    void approve_resumesTask() {
        Approval approval = new Approval();
        approval.setId("a-1");
        approval.setTaskId("task-1");
        approval.setPolicyId("p-1");
        approval.setStatus("pending");
        approval.setCreatedAt(LocalDateTime.now());

        ApprovalPolicy policy = new ApprovalPolicy();
        policy.setId("p-1");
        policy.setStrategy("require_one");

        when(approvalMapper.selectById("a-1")).thenReturn(approval);
        when(policyMapper.selectById("p-1")).thenReturn(policy);

        engine.decide("a-1", true, "approved by admin", "admin-1", "web");

        ArgumentCaptor<Approval> captor = ArgumentCaptor.forClass(Approval.class);
        verify(approvalMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("approved");
        verify(taskManagerService).working("task-1");
    }

    @Test
    void reject_cancelsTask() {
        Approval approval = new Approval();
        approval.setId("a-1");
        approval.setTaskId("task-1");
        approval.setPolicyId("p-1");
        approval.setStatus("pending");
        approval.setCreatedAt(LocalDateTime.now());

        ApprovalPolicy policy = new ApprovalPolicy();
        policy.setId("p-1");
        policy.setStrategy("require_one");

        when(approvalMapper.selectById("a-1")).thenReturn(approval);
        when(policyMapper.selectById("p-1")).thenReturn(policy);

        engine.decide("a-1", false, "rejected by admin", "admin-1", "web");

        ArgumentCaptor<Approval> captor = ArgumentCaptor.forClass(Approval.class);
        verify(approvalMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("rejected");
        verify(taskManagerService).finish(eq("task-1"), eq(TaskState.CANCELED), anyString(), any());
    }

    @Test
    void decideOnAlreadyDecided_throws() {
        Approval approval = new Approval();
        approval.setId("a-1");
        approval.setStatus("approved");

        when(approvalMapper.selectById("a-1")).thenReturn(approval);

        try {
            engine.decide("a-1", true, "again", "admin", "web");
            assert false : "should have thrown";
        } catch (com.magent.platform.common.BizException e) {
            assertThat(e.getMessage()).contains("审批已处理");
        }
    }
}