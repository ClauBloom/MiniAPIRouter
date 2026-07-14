package com.miniapi.router.saas.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码编码器配置类。
 * 
 * <p>配置 Spring Security 的密码编码器，使用 BCrypt 算法对用户密码进行加密存储。
 * BCrypt 是一种自适应哈希函数，内置随机盐值，能有效防止彩虹表攻击。
 */
@Configuration
public class PasswordConfig {

    /**
     * 创建 BCrypt 密码编码器 Bean。
     * <p>BCryptPasswordEncoder 会对密码进行加盐哈希处理，
     * 每次编码生成不同的结果，但可以通过 matches 方法验证密码正确性。
     *
     * @return BCrypt 密码编码器实例
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
