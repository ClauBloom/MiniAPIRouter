package com.miniapi.router.saas.event;

import com.miniapi.router.core.domain.RequestLogMeta;
import com.miniapi.router.core.spi.BlobStorage;
import com.miniapi.router.core.spi.LogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 日志持久化消费者
 * <p>
 * 监听 {@link LogPersistEvent} 日志持久化事件，将请求日志的元数据和内容异步持久化到存储中。
 * 该消费者通过 Spring 的异步事件机制工作，不会阻塞主请求线程。
 * </p>
 * <p>
 * 主要职责：
 * <ul>
 *   <li>将 Prompt 和 Response 内容存储到 Blob 存储（如本地文件系统）</li>
 *   <li>构建请求日志元数据对象并保存到数据库</li>
 * </ul>
 * </p>
 */
@Component
public class LogPersistConsumer {

    private static final Logger log = LoggerFactory.getLogger(LogPersistConsumer.class);

    private final LogRepository logRepository;  // 日志仓库，用于持久化日志元数据
    private final BlobStorage blobStorage;      // Blob 存储，用于存储 Prompt/Response 内容

    public LogPersistConsumer(LogRepository logRepository, BlobStorage blobStorage) {
        this.logRepository = logRepository;
        this.blobStorage = blobStorage;
    }

    /**
     * 处理日志持久化事件
     * <p>
     * 异步监听日志持久化事件，将请求的 Prompt 和 Response 内容存储到 Blob 存储，
     * 然后构建日志元数据对象并保存到数据库。
     * </p>
     *
     * @param event 日志持久化事件，包含请求的所有相关信息
     */
    @Async
    @EventListener
    public void onLogPersist(LogPersistEvent event) {
        try {
            String promptUrl = event.promptStorageUrl();
            String responseUrl = event.responseStorageUrl();

            // 如果 Prompt 存储地址为空但内容不为空，则将内容存储到 Blob 存储
            if (promptUrl == null && event.promptContent() != null && !event.promptContent().isEmpty()) {
                promptUrl = storeBlob(event.tenantId(), event.traceId(), "prompt", event.promptContent());
            }
            // 如果 Response 存储地址为空但内容不为空，则将内容存储到 Blob 存储
            if (responseUrl == null && event.responseContent() != null && !event.responseContent().isEmpty()) {
                responseUrl = storeBlob(event.tenantId(), event.traceId(), "response", event.responseContent());
            }

            // 构建请求日志元数据对象，填充各字段
            RequestLogMeta meta = new RequestLogMeta();
            meta.setTenantId(event.tenantId());
            meta.setUserId(event.userId());
            meta.setTraceId(event.traceId());
            meta.setRequestId(event.requestId());
            meta.setClientIp(event.clientIp());
            meta.setProtocol(event.protocol());
            meta.setModel(event.model());
            meta.setMappedProvider(event.mappedProvider() != null ? event.mappedProvider() : "unknown");
            meta.setApiKeyId(event.apiKeyId());
            meta.setRouteRuleId(event.routeRuleId());
            meta.setIntent(event.intent());
            meta.setPromptTokens(event.promptTokens());
            meta.setCompletionTokens(event.completionTokens());
            meta.setTotalTokens(event.totalTokens());
            meta.setLatencyMs(event.latencyMs());
            meta.setTtftMs(event.ttftMs());
            meta.setStatus(event.status());
            meta.setFallbackCount(event.fallbackCount());
            meta.setErrorCode(event.errorCode());
            meta.setErrorMessage(event.errorMessage());
            meta.setPromptStorageUrl(promptUrl);
            meta.setResponseStorageUrl(responseUrl);
            meta.setAgentId(event.agentId());
            meta.setAgentType(event.agentType());
            meta.setCreatedAt(event.createdAt() != null ? event.createdAt() : LocalDateTime.now());
            logRepository.save(meta);
        } catch (Exception e) {
            // 持久化失败时仅记录错误日志，不影响主流程
            log.error("[LogPersist] Failed to persist log for trace={}: {}", event.traceId(), e.getMessage());
        }
    }

    /**
     * 将内容存储到 Blob 存储
     * <p>
     * 按照租户ID/年/月/日/类型/traceId 的路径结构组织存储路径。
     * </p>
     *
     * @param tenantId 租户ID
     * @param traceId  链路追踪ID
     * @param type     内容类型（prompt 或 response）
     * @param content  待存储的内容
     * @return 存储路径
     */
    private String storeBlob(Long tenantId, String traceId, String type, String content) {
        LocalDateTime now = LocalDateTime.now();
        // 构建分层存储路径：tenant_{id}/{年}/{月}/{日}/{类型}/{traceId}.json
        String path = String.format("tenant_%d/%04d/%02d/%02d/%s/%s.json",
                tenantId, now.getYear(), now.getMonthValue(), now.getDayOfMonth(), type, traceId);
        return blobStorage.store(path, content);
    }
}
