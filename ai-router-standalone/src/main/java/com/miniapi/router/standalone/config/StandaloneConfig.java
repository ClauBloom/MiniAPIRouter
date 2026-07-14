package com.miniapi.router.standalone.config;

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
 * 独立版 Spring 配置类。
 * <p>
 * 配置 MyBatis-Plus 拦截器（分页）、自动填充字段处理器以及加密工具类。
 * </p>
 */
@Configuration
public class StandaloneConfig {

    /**
     * 配置 MyBatis-Plus 拦截器，添加 SQLite 分页插件。
     *
     * @return 配置好分页插件的 MyBatis-Plus 拦截器
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.SQLITE)); // SQLite 方言分页
        return interceptor;
    }

    /**
     * 配置 MyBatis-Plus 元对象处理器。
     * 在插入记录时自动填充 createdAt 和 updatedAt 字段，
     * 在更新记录时自动填充 updatedAt 字段。
     *
     * @return 元对象处理器
     */
    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, LocalDateTime.now());
                this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
            }
        };
    }

    /**
     * 创建加密工具类实例，用于 API Key 的加解密。
     *
     * @param properties 核心配置属性，包含加密密钥
     * @return 加密工具实例
     */
    @Bean
    public CryptoUtils cryptoUtils(CoreProperties properties) {
        return new CryptoUtils(properties.getCryptoSecret());
    }
}
