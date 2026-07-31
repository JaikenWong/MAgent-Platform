package com.magent.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.magent.platform.common.BizException;
import com.magent.platform.common.JwtUtil;
import com.magent.platform.dto.LoginRequest;
import com.magent.platform.dto.LoginResponse;
import com.magent.platform.entity.Admin;
import com.magent.platform.mapper.AdminMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AdminMapper adminMapper;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder encoder;

    public LoginResponse login(LoginRequest req) {
        Admin admin = adminMapper.selectOne(
                new QueryWrapper<Admin>().eq("username", req.username()));
        if (admin == null || !encoder.matches(req.password(), admin.getPasswordHash())) {
            throw new BizException(401, "用户名或密码错误");
        }
        String token = jwtUtil.generate(admin.getId(), admin.getUsername(), admin.getRole());
        log.info("admin {} logged in", admin.getUsername());
        return new LoginResponse(token, admin.getId(), admin.getUsername(), admin.getRole());
    }
}