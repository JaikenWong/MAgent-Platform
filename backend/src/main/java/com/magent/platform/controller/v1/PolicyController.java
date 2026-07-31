package com.magent.platform.controller.v1;

import com.magent.platform.entity.ApprovalPolicy;
import com.magent.platform.mapper.ApprovalPolicyMapper;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/approval-policies")
public class PolicyController extends CrudController<ApprovalPolicy, ApprovalPolicyMapper> {

    @Override
    protected Class<ApprovalPolicy> entityClass() { return ApprovalPolicy.class; }
}