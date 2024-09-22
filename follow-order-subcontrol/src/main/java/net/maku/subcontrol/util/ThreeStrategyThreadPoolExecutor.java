package net.maku.subcontrol.util;

import lombok.extern.slf4j.Slf4j;
import net.maku.subcontrol.callable.ApiAnalysisCallable;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.*;

/**
 *
 * @author Samson Bruce
 */
@Slf4j
public class ThreeStrategyThreadPoolExecutor extends ThreadPoolExecutor {
    public ThreeStrategyThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, @NotNull TimeUnit unit, @NotNull BlockingQueue<Runnable> workQueue) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
    }

    public BlockingQueue<String> running = new LinkedBlockingQueue<>();


    public synchronized Future<Boolean> submit(@NotNull ApiAnalysisCallable apiAnalysisCallable) {
        if (running.contains(apiAnalysisCallable.getTrader().getId())) {
            return null;
        } else {
            log.info(apiAnalysisCallable.getTrader().getAccount() + " 添加apiAnalysis成功");
            running.add(apiAnalysisCallable.getTrader().getId().toString());
            return super.submit(apiAnalysisCallable);
        }
    }

}
