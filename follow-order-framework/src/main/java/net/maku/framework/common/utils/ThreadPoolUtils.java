package net.maku.framework.common.utils;

import cn.hutool.core.thread.ThreadFactoryBuilder;
import cn.hutool.core.thread.ThreadUtil;
import lombok.Getter;

import java.util.concurrent.*;

public class ThreadPoolUtils {

    // 定义一个固定大小的线程池
    private static final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();

    // 定义一个固定大小的延时任务的线程池
    private static final ScheduledThreadPoolExecutor scheduledExecutorService = ThreadUtil.createScheduledExecutor(500);

    // 定义一个固定大小的延时任务的线程池
    private static final ExecutorService  scheduledExecutorServiceOrder=  Executors.newVirtualThreadPerTaskExecutor();;
    // 用户下单及平仓线程处理
    @Getter
    private static final ExecutorService scheduledExecutorSend=Executors.newVirtualThreadPerTaskExecutor();

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
     * 提交延时任务到线程池执行
     */
    public static ExecutorService getScheduledExecuteOrder() {
        return scheduledExecutorServiceOrder;
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
