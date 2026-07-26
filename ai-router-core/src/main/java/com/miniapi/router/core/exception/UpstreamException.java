package com.miniapi.router.core.exception;

/**
 * 上游服务异常。
 * <p>
 * 当向上游 AI 服务商转发请求失败时抛出。
 * 默认使用 UPSTREAM_ERROR 错误码和 502 状态码，
 * 支持通过子类构造器传入自定义错误码和状态码以描述具体失败原因。
 * </p>
 * <p>
 * 携带上游原始 HTTP 状态码 {@link #getUpstreamStatus()}，用于区分两类错误：
 * <ul>
 *   <li><b>可回退错误</b>（failover eligible）：临时性故障（限流、5xx、网络超时等），
 *       换一个上游 Key 重试可能成功；</li>
 *   <li><b>不可回退错误</b>：请求本身的确定性错误（如 400 参数错误、404、413 请求体过大），
 *       换任何 Key 都会得到相同结果，应立即将真实错误返回给客户端，避免浪费回退次数。</li>
 * </ul>
 * 分类规则参考业界网关实践：401/402/403/408/429/529 及所有 5xx 视为可回退，
 * 其余 4xx 视为请求级错误不回退；状态码未知（网络错误/超时，即 0）视为可回退。
 * </p>
 */
public class UpstreamException extends RouterException {

    /** 上游返回的原始 HTTP 状态码；0 表示未知（连接失败、超时等未收到响应的场景） */
    private final int upstreamStatus;

    /**
     * 使用默认上游错误码构造异常（上游状态码未知）。
     *
     * @param message 错误描述信息
     */
    public UpstreamException(String message) {
        this(message, 0);
    }

    /**
     * 使用上游状态码构造异常。
     * 对外 HTTP 状态码映射规则：上游 4xx（请求级错误）原样透传，其余映射为 502。
     *
     * @param message        错误描述信息
     * @param upstreamStatus 上游返回的原始 HTTP 状态码，0 表示未知
     */
    public UpstreamException(String message, int upstreamStatus) {
        super("UPSTREAM_ERROR", message, mapHttpStatus(upstreamStatus));
        this.upstreamStatus = upstreamStatus;
    }

    /**
     * 使用自定义错误码构造异常，用于细分上游错误类型（如超时、限流等）。
     *
     * @param errorCode  业务错误码
     * @param message    错误描述信息
     * @param httpStatus HTTP 状态码
     */
    public UpstreamException(String errorCode, String message, int httpStatus) {
        super(errorCode, message, httpStatus);
        this.upstreamStatus = 0;
    }

    /** @return 上游返回的原始 HTTP 状态码，0 表示未知 */
    public int getUpstreamStatus() {
        return upstreamStatus;
    }

    /** @return 该错误是否值得切换到下一个上游 Key 重试 */
    public boolean isFailoverEligible() {
        return isFailoverEligible(upstreamStatus);
    }

    /**
     * 判断指定上游状态码是否应触发回退（failover）。
     * <ul>
     *   <li>401/403：凭证问题，属于该 Key 自身故障，换 Key 可能成功</li>
     *   <li>402：余额不足，同上</li>
     *   <li>408/429/529：超时、限流、过载，临时性故障</li>
     *   <li>&ge;500：上游服务端故障</li>
     *   <li>&le;0：未收到响应（连接失败/超时），视为临时性故障</li>
     *   <li>其余 4xx（400/404/413/422 等）：请求级确定性错误，换 Key 无意义</li>
     * </ul>
     *
     * @param upstreamStatus 上游 HTTP 状态码
     * @return true 表示可以切换下一个 Key 重试
     */
    public static boolean isFailoverEligible(int upstreamStatus) {
        if (upstreamStatus <= 0) {
            return true;
        }
        return switch (upstreamStatus) {
            case 401, 402, 403, 408, 429, 529 -> true;
            default -> upstreamStatus >= 500;
        };
    }

    /** 上游 4xx 请求级错误原样透传给客户端，其余（5xx/未知）统一映射为 502 Bad Gateway */
    private static int mapHttpStatus(int upstreamStatus) {
        return upstreamStatus >= 400 && upstreamStatus < 500 ? upstreamStatus : 502;
    }
}
