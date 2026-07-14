package com.miniapi.router.standalone.spiimpl;

import com.miniapi.router.core.spi.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 本地事件发布器实现。
 * <p>
 * 实现 EventPublisher SPI 接口，独立版不使用消息队列，
 * 仅以 DEBUG 日志方式同步记录事件，不做实际的事件分发。
 * </p>
 */
@Component
public class LocalEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LocalEventPublisher.class);

    /**
     * 发布日志事件（同步记录 DEBUG 日志）。
     *
     * @param event 事件对象
     */
    @Override
    public void publishLogEvent(Object event) {
        log.debug("[Event] Log event published (synchronous): {}", event.getClass().getSimpleName());
    }

    /**
     * 发布使用统计事件（同步记录 DEBUG 日志）。
     *
     * @param event 事件对象
     */
    @Override
    public void publishUsageStatsEvent(Object event) {
        log.debug("[Event] Usage stats event published (synchronous): {}", event.getClass().getSimpleName());
    }
}
