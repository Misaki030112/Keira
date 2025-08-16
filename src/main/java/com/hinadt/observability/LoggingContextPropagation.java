package com.hinadt.observability;

import org.slf4j.MDC;
import reactor.core.scheduler.Schedulers;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Propagates logging/request context across Reactor thread switches.
 *
 * Why: Spring AI ChatClient executes network and tool-calling logic on Reactor
 * threads. Without propagation, ThreadLocal-based MDC/RequestContext set on the
 * caller thread will be lost, and tool logs cannot access the messageId.
 *
 * Approach: Install a Reactor schedule hook that captures MDC and RequestContext
 * at scheduling time and restores them when the task runs, then cleans up.
 */
public final class LoggingContextPropagation {

    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);
    private static final String HOOK_KEY = "ausuka-request-context";

    private LoggingContextPropagation() {}

    /**
     * Install the propagation hook once for the process.
     */
    public static void install() {
        if (INSTALLED.compareAndSet(false, true)) {
            Schedulers.onScheduleHook(HOOK_KEY, runnable -> {
                // Capture context at scheduling time (caller thread)
                Map<String, String> capturedMdc = MDC.getCopyOfContextMap();
                String capturedMid = RequestContext.getMessageId();

                return () -> {
                    // Save current context to restore after execution
                    Map<String, String> previousMdc = MDC.getCopyOfContextMap();
                    String previousMid = RequestContext.getMessageId();
                    try {
                        // Restore captured context for this task
                        if (capturedMdc != null) {
                            MDC.setContextMap(capturedMdc);
                        } else {
                            MDC.clear();
                        }
                        if (capturedMid != null) {
                            RequestContext.setMessageId(capturedMid);
                        } else {
                            RequestContext.clear();
                        }
                        runnable.run();
                    } finally {
                        // Restore previous context to avoid leaking across tasks
                        if (previousMdc != null) {
                            MDC.setContextMap(previousMdc);
                        } else {
                            MDC.clear();
                        }
                        if (previousMid != null) {
                            RequestContext.setMessageId(previousMid);
                        } else {
                            RequestContext.clear();
                        }
                    };
                };
            });
        }
    }
}

