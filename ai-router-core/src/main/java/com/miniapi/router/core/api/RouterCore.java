package com.miniapi.router.core.api;

import java.io.OutputStream;

/**
 * Core 的唯一代理执行门面。
 * 宿主依赖此接口，因此可在进程内实现和未来远程微服务客户端之间切换。
 * <p>
 * 两类使用方式：
 * <ul>
 *   <li><b>一步式</b>：{@link #proxy} / {@link #proxyStream} —— 路由 + 执行一次完成（HTTP 宿主）</li>
 *   <li><b>两阶段</b>：{@link #plan} → {@link #execute} / {@link #executeStream} ——
 *       先拿到不可变的 {@link RoutePlan} 决策快照，再执行；也可仅用于观测"这条请求会走哪个模型"，
 *       或交给 Spring AI 侧绑定为 {@code ChatModel}</li>
 * </ul>
 */
public interface RouterCore {

    /* ---------- 一步式 API（原有，语义不变） ---------- */

    RouterResult proxy(RouterRequest request);

    RouterResult proxyStream(RouterRequest request, OutputStream output);

    /* ---------- 两阶段 API（Plan / Execute） ---------- */

    /**
     * 只做路由决策，不调用上游。
     * <p>
     * 完成规则匹配、（必要时）意图评估与策略选择，并基于选中的上游协议
     * 构建好上游请求体。返回的 {@link RoutePlan} 是不可变快照，可：
     * 交给 {@link #execute} / {@link #executeStream} 执行、用于可观测/成本预估、
     * 或绑定成 Spring AI 的 {@code ChatModel}。
     *
     * @throws com.miniapi.router.core.exception.RouterException 无匹配路由 / 无可用上游时
     */
    RoutePlan plan(RouterRequest request);

    /**
     * 使用已有 Plan 执行非流式调用。
     * Plan 中的上游请求体在执行时可被替换为 fallback 链中目标的真实模型名。
     */
    RouterResult execute(RoutePlan plan);

    /**
     * 使用已有 Plan 执行流式调用，SSE 字节写到 {@code output}（HTTP 宿主）。
     */
    RouterResult executeStream(RoutePlan plan, OutputStream output);

    /**
     * 使用已有 Plan 执行流式调用，通过类型化 {@link com.miniapi.router.core.streaming.StreamSink}
     * 逐块输出（Spring AI 等进程内消费方）。
     */
    RouterResult executeStream(RoutePlan plan, com.miniapi.router.core.streaming.StreamSink sink);
}
