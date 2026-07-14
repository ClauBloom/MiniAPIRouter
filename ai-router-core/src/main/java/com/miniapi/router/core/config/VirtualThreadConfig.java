package com.miniapi.router.core.config;

import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executors;

/**
 * 虚拟线程配置类。
 * <p>
 * 将 Spring 默认的异步任务执行器替换为 Java 21 虚拟线程执行器，
 * 使所有标注 {@link org.springframework.scheduling.annotation.Async} 的方法
 * 均在虚拟线程上运行，实现高并发下的低开销异步处理。
 * </p>
 */
@Configuration
@EnableAsync
public class VirtualThreadConfig {

    /**
     * 提供基于虚拟线程的异步任务执行器。
     * <p>
     * 覆盖 Spring Boot 自动配置中的 {@code applicationTaskExecutor} Bean，
     * 使用 {@link Executors#newVirtualThreadPerTaskExecutor()} 创建虚拟线程池。
     * </p>
     *
     * @return 虚拟线程异步任务执行器
     */
    @Bean(TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME)
    public AsyncTaskExecutor applicationTaskExecutor() {
        return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
    }
}
