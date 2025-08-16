package com.hinadt.observability;

import org.slf4j.MDC;

/**
 * 观测性请求上下文
 * - 使用 ThreadLocal 存放本次用户消息对应的 messageId
 * - 通过 MDC 注入日志上下文（若日志配置支持）
 * - 提供统一的日志前缀，便于在日志中检索溯源
 */
public final class RequestContext {
    private static final ThreadLocal<String> MESSAGE_ID = new ThreadLocal<>();
    private static final String MDC_KEY = "messageId";

    private RequestContext() {}

    public static void setMessageId(String messageId) {
        MESSAGE_ID.set(messageId);
        if (messageId != null) {
            MDC.put(MDC_KEY, messageId);
        }
    }

    public static String getMessageId() {
        return MESSAGE_ID.get();
    }

    public static void clear() {
        MESSAGE_ID.remove();
        MDC.remove(MDC_KEY);
    }

    /**
     * 返回标准日志前缀，例如：[mid=xxxx]
     */
    public static String midTag() {
        String mid = getMessageId();
        return mid == null ? "[mid=-]" : "[mid=" + mid + "]";
    }
}

