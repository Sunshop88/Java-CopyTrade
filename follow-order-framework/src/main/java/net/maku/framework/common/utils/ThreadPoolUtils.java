package net.maku.framework.common.utils;

import cn.hutool.core.thread.ThreadFactoryBuilder;
import cn.hutool.core.thread.ThreadUtil;
import lombok.Getter;

import java.util.concurrent.*;

public class ThreadPoolUtils {

    // 定义一个固定大小的线程池
    private static final ExecutorService executorService = ThreadUtil.newFixedExecutor(20,"POOLFIXED",true);

    // 定义一个固定大小的延时任务的线程池
    private static final ScheduledThreadPoolExecutor scheduledExecutorService = ThreadUtil.createScheduledExecutor(200);
    // 定义一个固定大小的延时任务的线程池
    private static final ScheduledThreadPoolExecutor scheduledstart= ThreadUtil.createScheduledExecutor(30);
    // 用户下单及平仓线程处理
    @Getter
    private static final ThreadPoolExecutor scheduledExecutorSend=new ThreadPoolExecutor(
            20, // 核心线程数
            500, // 最大线程数
            60L, TimeUnit.SECONDS, // 空闲线程存活时间
            new LinkedBlockingQueue<>(10000), // 队列大小
            new ThreadPoolExecutor.CallerRunsPolicy() // 队列满时执行的策略
    );
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

    public static ScheduledThreadPoolExecutor getScheduledstartStart() {
        return scheduledstart;
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
