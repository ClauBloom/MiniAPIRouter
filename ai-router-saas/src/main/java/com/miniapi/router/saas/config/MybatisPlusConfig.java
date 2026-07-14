package com.miniapi.router.saas.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.miniapi.router.core.config.CoreProperties;
import com.miniapi.router.core.util.CryptoUtils;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 框架配置类。
 * 
 * <p>配置 MyBatis-Plus 的核心拦截器和功能组件，包括：
 * <ul>
 *   <li>分页拦截器：自动处理分页查询</li>
 *   <li>元对象处理器：自动填充创建时间和更新时间</li>
 *   <li>加密工具：用于 API Key 等敏感信息的加解密</li>
 * </ul>
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 配置 MyBatis-Plus 拦截器，添加分页内联拦截器。
     * <p>使用 MariaDB 数据库类型，支持自动分页 SQL 拼接。
     *
     * @return 配置好分页拦截器的 MyBatis-Plus 拦截器
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MARIADB));
        return interceptor;
    }

    /**
     * 配置元对象处理器，用于自动填充数据库记录的创建时间和更新时间。
     * <p>在执行 insert 操作时自动填充 createdAt 和 updatedAt 字段；
     * 在执行 update 操作时自动填充 updatedAt 字段。
     *
     * @return 元对象处理器实例
     */
    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                // 插入时自动填充创建时间和更新时间
                this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, LocalDateTime.now());
                this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                // 更新时自动填充更新时间
                this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
            }
        };
    }

    /**
     * 创建加密工具 Bean，用于 API Key 等敏感数据的加解密操作。
     *
     * @param properties 核心配置属性，提供加密密钥
     * @return 加密工具实例
     */
    @Bean
    public CryptoUtils cryptoUtils(CoreProperties properties) {
        return new CryptoUtils(properties.getCryptoSecret());
    }
}
