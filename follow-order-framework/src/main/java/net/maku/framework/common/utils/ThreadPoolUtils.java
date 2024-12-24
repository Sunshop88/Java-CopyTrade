package net.maku.framework.common.utils;

import cn.hutool.core.thread.ThreadFactoryBuilder;
import cn.hutool.core.thread.ThreadUtil;
import lombok.Getter;

import java.util.concurrent.*;

public class ThreadPoolUtils {

    // 虚拟线程
    private static final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();

    public static ExecutorService getExecutor() {
        return executorService;
    }
    /**
     * 提交任务到线程池执行
     *
     * @param runnable 要执行的任务
     */
    public static void execute(Runnable runnable) {
        executorService.execute(runnable);
    }


    /**
     * 优雅地关闭线程池
     */
    public static void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 立即关闭线程池
     */
    public static void shutdownNow() {
        executorService.shutdownNow();
    }

    /**
     * 判断线程池是否已关闭
     *
     * @return true 如果线程池已关闭
     */
    public static boolean isShutdown() {
        return executorService.isShutdown();
    }
}
