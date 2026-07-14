package com.miniapi.router.saas.spiimpl;

import com.miniapi.router.core.spi.EventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 异步事件发布器
 * <p>
 * 实现 {@link EventPublisher} SPI 接口，基于 Spring 的 {@link ApplicationEventPublisher} 发布事件。
 * 事件由 Spring 容器异步处理（配合 @Async 注解的监听器），实现日志和统计事件的解耦发布。
 * </p>
 */
@Component
public class AsyncEventPublisher implements EventPublisher {

    private final ApplicationEventPublisher publisher;  // Spring 应用事件发布器

    public AsyncEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    /**
     * 发布日志事件
     * <p>
     * 将日志事件通过 Spring 事件机制发布，由异步监听器消费处理。
     * </p>
     *
     * @param event 日志事件对象
     */
    @Override
    public void publishLogEvent(Object event) {
        publisher.publishEvent(event);
    }

    /**
     * 发布使用统计事件
     * <p>
     * 将使用统计事件通过 Spring 事件机制发布，由异步监听器消费处理。
     * </p>
     *
     * @param event 使用统计事件对象
     */
    @Override
    public void publishUsageStatsEvent(Object event) {
        publisher.publishEvent(event);
    }
}
