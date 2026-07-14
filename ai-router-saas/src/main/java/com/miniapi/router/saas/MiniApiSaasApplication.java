package com.miniapi.router.saas;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * MiniAPIRouter SaaS 模块的 Spring Boot 应用启动入口类。
 * 
 * <p>该类负责启动整个 SaaS 平台服务，主要功能包括：
 * <ul>
 *   <li>扫描 saas 与 core 两个包下的组件，完成 Spring 容器初始化</li>
 *   <li>扫描 MyBatis-Plus Mapper 接口</li>
 *   <li>开启异步任务支持（@EnableAsync）</li>
 *   <li>开启定时任务支持（@EnableScheduling）</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = {"com.miniapi.router.saas", "com.miniapi.router.core"})
@MapperScan("com.miniapi.router.saas.mapper")  // 扫描 Mapper 接口所在包，注册为 Spring Bean
@EnableAsync      // 启用异步方法执行支持，配合 @Async 注解使用
@EnableScheduling // 启用定时任务调度支持，配合 @Scheduled 注解使用
public class MiniApiSaasApplication {
    /**
     * 应用程序主入口方法，启动 Spring Boot 容器。
     *
     * @param args 命令行启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(MiniApiSaasApplication.class, args);
    }
}
