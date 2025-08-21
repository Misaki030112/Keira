package com.keira.observability;

import org.slf4j.MDC;

/**
 * Observable request context
 * - Use ThreadLocal to store the messageId corresponding to the current user message
 * - Inject the logging context via MDC (if supported by the logging configuration)
 * - Provide a unified log prefix to facilitate retrieval and tracing in logs
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
     * Returns the standard log prefix, for example: [mid=xxxx]
     */
    public static String midTag() {
        String mid = getMessageId();
        return mid == null ? "[mid=-]" : "[mid=" + mid + "]";
    }
}

