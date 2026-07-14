package com.miniapi.router.core.exception;

/**
 * 所有上游均失败的异常。
 * <p>
 * 当路由过程中所有候选上游（包括回退链路）均请求失败时抛出此异常，
 * 对应 HTTP 502 状态码。
 * </p>
 */
public class AllUpstreamFailedException extends RouterException {
    public AllUpstreamFailedException(String message) {
        super("ALL_UPSTREAM_FAILED", message, 502);
    }
}
