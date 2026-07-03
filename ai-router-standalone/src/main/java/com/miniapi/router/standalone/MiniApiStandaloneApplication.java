package com.miniapi.router.standalone;

import com.miniapi.router.standalone.config.SetupWizard;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

import java.nio.file.Files;
import java.nio.file.Path;

@SpringBootApplication(scanBasePackages = {"com.miniapi.router.standalone", "com.miniapi.router.core"})
@MapperScan("com.miniapi.router.standalone.mapper")
@EnableAsync
public class MiniApiStandaloneApplication {

    public static void main(String[] args) {
        ensureDataDirectories();
        SetupWizard.runIfFirstTime(args);
        System.setProperty("miniapi.router.crypto-secret", SetupWizard.ensureCryptoSecret());
        SpringApplication.run(MiniApiStandaloneApplication.class, args);
    }

    private static void ensureDataDirectories() {
        try {
            Path baseDir = SetupWizard.getBaseDir();
            Files.createDirectories(baseDir);
            Files.createDirectories(baseDir.resolve("logs"));
            System.setProperty("miniapi.router.data-dir", baseDir.toString());
        } catch (Exception e) {
            System.err.println("[Init] Failed to create data directories: " + e.getMessage());
        }
    }
}
