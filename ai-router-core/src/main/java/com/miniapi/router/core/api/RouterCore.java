package com.miniapi.router.core.api;

import java.io.OutputStream;

/**
 * Core 的唯一代理执行门面。
 * 宿主依赖此接口，因此可在进程内实现和未来远程微服务客户端之间切换。
 */
public interface RouterCore {

    RouterResult proxy(RouterRequest request);

    RouterResult proxyStream(RouterRequest request, OutputStream output);
}
