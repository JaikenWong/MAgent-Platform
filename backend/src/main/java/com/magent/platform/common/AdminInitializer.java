package com.magent.platform.common;

import com.magent.platform.entity.Admin;
import com.magent.platform.mapper.AdminMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInitializer implements CommandLineRunner {

    private final AdminMapper adminMapper;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Value("${magent.default-admin.username:admin}")
    private String username;
    @Value("${magent.default-admin.password:admin123}")
    private String password;

    @Override
    public void run(String... args) {
        Admin existing = adminMapper.selectOne(
                new QueryWrapper<Admin>().eq("username", username));
        if (existing != null) {
            log.info("default admin '{}' already exists, skip init", username);
            return;
        }
        Admin admin = new Admin();
        admin.setUsername(username);
        admin.setPasswordHash(encoder.encode(password));
        admin.setRole("super_admin");
        adminMapper.insert(admin);
        log.info("default admin '{}' created with role super_admin", username);
    }
}