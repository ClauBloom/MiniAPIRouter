package com.miniapi.router.core.spi;

/**
 * 事件发布器接口（SPI 扩展点）。
 * <p>
 * 负责发布路由过程中产生的各类事件，包括请求日志事件和用量统计事件。
 * 具体实现可将事件投递到消息队列、日志系统或统计服务中。
 * </p>
 */
public interface EventPublisher {

    /** 发布请求日志事件（路由请求的完整生命周期记录） */
    void publishLogEvent(Object event);

    /** 发布用量统计事件（Token 消耗、调用次数等统计信息） */
    void publishUsageStatsEvent(Object event);
}
