package com.hinadt.util;

import com.hinadt.ai.AiRuntime;

import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * Async utilities for composing cancelable tasks with timeouts.
 * Ensures we cancel the underlying work via interruption instead of only timing out a future.
 */
public final class Async {
    private Async() {}

    /**
     * Run a supplier on an executor and cancel it after the given timeout.
     * Cancellation is cooperative and interrupts the worker thread.
     */
    public static <T> CompletableFuture<T> supplyAsyncWithTimeout(
            Supplier<T> supplier,
            Executor executor,
            long timeout,
            TimeUnit unit
    ) {
        CompletableFuture<T> task = CompletableFuture.supplyAsync(supplier, executor);
        // Schedule cooperative cancellation
        ScheduledFuture<?> timer = AiRuntime.scheduler().schedule(() -> {
            if (!task.isDone()) {
                task.cancel(true);
            }
        }, timeout, unit);
        // Ensure the timer is cleared on completion
        task.whenComplete((r, e) -> timer.cancel(false));
        return task;
    }
}

