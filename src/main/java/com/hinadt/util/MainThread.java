package com.hinadt.util;

import net.minecraft.server.MinecraftServer;

import java.util.concurrent.CountDownLatch;
import java.util.function.Supplier;

/**
 * Server main-thread helpers.
 * Ensures game-state mutations run on the server thread and optionally wait for completion.
 */
public final class MainThread {
    private MainThread() {}

    /** Run a task on the server main thread and block until it completes. */
    public static void runSync(MinecraftServer server, Runnable task) {
        if (server == null || server.isOnThread()) {
            task.run();
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        server.execute(() -> {
            try { task.run(); } finally { latch.countDown(); }
        });
        try {
            latch.await();
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    /** Call a supplier on the server main thread and return its result. */
    public static <T> T callSync(MinecraftServer server, Supplier<T> supplier) {
        final Object[] box = new Object[1];
        runSync(server, () -> box[0] = supplier.get());
        @SuppressWarnings("unchecked")
        T t = (T) box[0];
        return t;
    }
}

