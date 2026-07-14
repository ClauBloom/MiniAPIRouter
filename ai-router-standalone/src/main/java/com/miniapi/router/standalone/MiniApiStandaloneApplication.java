package com.miniapi.router.standalone;

import com.miniapi.router.standalone.config.SetupWizard;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * MiniAPIRouter 独立版启动入口类。
 * <p>
 * 该类是独立部署模式下的 Spring Boot 应用主类，负责：
 * 1. 创建数据目录（数据库文件、日志文件等）
 * 2. 首次启动时运行配置向导（SetupWizard）
 * 3. 设置加密密钥等系统属性
 * 4. 启动 Spring Boot 应用
 * </p>
 * 扫描包包括 standalone 模块自身和 core 核心模块。
 */
@SpringBootApplication(scanBasePackages = {"com.miniapi.router.standalone", "com.miniapi.router.core"})
@MapperScan("com.miniapi.router.standalone.mapper") // 扫描 MyBatis-Plus Mapper 接口
@EnableAsync // 启用异步方法支持（用于日志异步写入等场景）
public class MiniApiStandaloneApplication {

    /**
     * 应用主入口方法。
     * 启动流程：创建数据目录 -> 首次启动配置向导 -> 设置加密密钥 -> 启动 Spring Boot
     *
     * @param args 命令行参数，支持 --skip-setup 跳过首次配置向导
     */
    public static void main(String[] args) {
        ensureDataDirectories(); // 确保数据目录存在
        SetupWizard.runIfFirstTime(args); // 首次启动时运行配置向导
        System.setProperty("miniapi.router.crypto-secret", SetupWizard.ensureCryptoSecret()); // 确保加密密钥已设置
        SpringApplication.run(MiniApiStandaloneApplication.class, args); // 启动 Spring Boot 应用
    }

    /**
     * 确保数据目录存在。
     * 在用户主目录下创建 .miniapirouter 目录及其 logs 子目录，
     * 并将该路径设置为系统属性 miniapi.router.data-dir 供其他组件使用。
     */
    private static void ensureDataDirectories() {
        try {
            Path baseDir = SetupWizard.getBaseDir(); // 获取基础数据目录路径
            Files.createDirectories(baseDir); // 创建基础目录
            Files.createDirectories(baseDir.resolve("logs")); // 创建日志子目录
            System.setProperty("miniapi.router.data-dir", baseDir.toString()); // 设置为系统属性
        } catch (Exception e) {
            System.err.println("[Init] Failed to create data directories: " + e.getMessage());
        }
    }
}
