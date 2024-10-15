package net.maku.framework.common.utils;

import cn.hutool.core.thread.ThreadFactoryBuilder;
import cn.hutool.core.thread.ThreadUtil;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ThreadPoolUtils {

    // 定义一个固定大小的线程池
    private static final ExecutorService executorService = ThreadUtil.newExecutor(10, 20,1000);

    // 定义一个固定大小的延时任务的线程池
    private static final ScheduledThreadPoolExecutor scheduledExecutorService = ThreadUtil.createScheduledExecutor(200);


    /**
     * 提交任务到线程池执行
     *
     * @param runnable 要执行的任务
     */
    public static void execute(Runnable runnable) {
        executorService.execute(runnable);
    }

    /**
     * 提交延时任务到线程池执行
     *
     * @param runnable 要执行的任务
     */
    public static void scheduledExecute(Runnable runnable,long time,TimeUnit timeUnit) {
        scheduledExecutorService.schedule(runnable,time,timeUnit);
    }

    /**
     * 提交延时任务到线程池执行
     */
    public static ScheduledThreadPoolExecutor getScheduledExecute() {
        return scheduledExecutorService;
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
