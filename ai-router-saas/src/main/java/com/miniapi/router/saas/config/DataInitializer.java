package com.miniapi.router.saas.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.miniapi.router.saas.entity.SysUserDO;
import com.miniapi.router.saas.entity.TenantDO;
import com.miniapi.router.saas.mapper.SysUserMapper;
import com.miniapi.router.saas.mapper.TenantMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 数据初始化器，在应用启动时自动执行。
 * 
 * <p>实现 {@link CommandLineRunner} 接口，在 Spring Boot 应用启动完成后自动执行数据初始化逻辑。
 * 主要职责：
 * <ul>
 *   <li>创建超级管理员账户（admin），用于管理整个 SaaS 平台</li>
 *   <li>创建演示租户（demo）及其租户管理员账户，方便用户体验和测试</li>
 * </ul>
 * 所有初始化操作均为幂等执行——仅在对应数据不存在时才创建。
 */
@Slf4j
@Component
public class DataInitializer implements CommandLineRunner {

    private final TenantMapper tenantMapper;       // 租户表 Mapper，用于操作租户数据
    private final SysUserMapper userMapper;        // 系统用户表 Mapper，用于操作用户数据
    private final PasswordEncoder passwordEncoder; // 密码编码器，用于加密用户密码

    @Value("${SAAS_ADMIN_DEFAULT_PASSWORD:admin123}")
    private String adminDefaultPassword; // 超级管理员的默认密码，可通过环境变量覆盖

    @Value("${SAAS_DEMO_ADMIN_DEFAULT_PASSWORD:demo123}")
    private String demoAdminDefaultPassword; // 演示租户管理员的默认密码，可通过环境变量覆盖

    /**
     * 构造函数注入所需的 Mapper 和密码编码器。
     *
     * @param tenantMapper    租户 Mapper
     * @param userMapper      用户 Mapper
     * @param passwordEncoder 密码编码器
     */
    public DataInitializer(TenantMapper tenantMapper, SysUserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.tenantMapper = tenantMapper;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 应用启动后自动执行的初始化逻辑。
     * <p>依次创建超级管理员和演示租户数据（仅在数据不存在时创建）。
     *
     * @param args 启动参数
     */
    @Override
    public void run(String... args) {
        // === 创建超级管理员账户 ===
        // 检查是否已存在用户名为 admin 的账户，不存在则创建
        LambdaQueryWrapper<SysUserDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUserDO::getUsername, "admin");
        if (userMapper.selectCount(wrapper) == 0) {
            SysUserDO admin = new SysUserDO();
            admin.setTenantId(0L);                                    // 租户ID为0，表示平台级别的超级管理员
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode(adminDefaultPassword)); // 对密码进行 BCrypt 加密
            admin.setNickname("超级管理员");
            admin.setRole("super_admin");                              // 角色为超级管理员
            admin.setStatus(1);                                        // 状态为启用
            userMapper.insert(admin);
            log.info("Created super admin: admin / {}", adminDefaultPassword);
        }

        // === 创建演示租户及其管理员 ===
        // 检查是否已存在租户编码为 demo 的租户，不存在则创建
        LambdaQueryWrapper<TenantDO> tenantWrapper = new LambdaQueryWrapper<>();
        tenantWrapper.eq(TenantDO::getTenantCode, "demo");
        if (tenantMapper.selectCount(tenantWrapper) == 0) {
            // 创建演示租户
            TenantDO tenant = new TenantDO();
            tenant.setTenantCode("demo");
            tenant.setTenantName("Demo Corporation");
            tenant.setPlan("pro");                  // 套餐为专业版
            tenant.setQuotaLimit(50000000L);        // 配额上限：5000万
            tenant.setQuotaUsed(0L);                // 已用配额：0
            tenant.setQuotaResetDay(1);             // 每月1号重置配额
            tenant.setMaxRps(100);                  // 最大并发请求数：100
            tenant.setStatus(1);                    // 状态为启用
            tenantMapper.insert(tenant);
            log.info("Created default tenant: demo (id={})", tenant.getId());

            // 为演示租户创建租户管理员账户
            SysUserDO tenantAdmin = new SysUserDO();
            tenantAdmin.setTenantId(tenant.getId());  // 关联刚创建的租户ID
            tenantAdmin.setUsername("demo_admin");
            tenantAdmin.setPassword(passwordEncoder.encode(demoAdminDefaultPassword));
            tenantAdmin.setNickname("Demo Admin");
            tenantAdmin.setRole("tenant_admin");       // 角色为租户管理员
            tenantAdmin.setStatus(1);
            userMapper.insert(tenantAdmin);
            log.info("Created tenant admin: demo_admin / {}", demoAdminDefaultPassword);
        }
    }
}
