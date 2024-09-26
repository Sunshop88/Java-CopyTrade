package net.maku.mascontrol.even;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.service.FollowTraderService;
import net.maku.followcom.service.impl.FollowTraderServiceImpl;
import net.maku.mascontrol.trader.AbstractApiTrader;
import net.maku.mascontrol.util.SpringContextUtils;
import online.mtapi.mt4.QuoteClient;
import online.mtapi.mt4.QuoteEventArgs;
import online.mtapi.mt4.QuoteEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class OnQuoteTraderHandler implements QuoteEventHandler {
    private static final Logger log = LoggerFactory.getLogger(OnQuoteTraderHandler.class);
    protected FollowTraderEntity leader;
    protected AbstractApiTrader abstractApiTrader;
    protected ScheduledThreadPoolExecutor scheduledThreadPoolExecutor;
    protected FollowTraderService followTraderService;
    protected Boolean running = Boolean.TRUE;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final AtomicBoolean isInvokeAllowed = new AtomicBoolean(true); // 使用AtomicBoolean，保证线程安全

    public OnQuoteTraderHandler(AbstractApiTrader abstractApiTrader ) {
        this.abstractApiTrader=abstractApiTrader;
        this.scheduledThreadPoolExecutor = SpringContextUtils.getBean("scheduledExecutorService", ScheduledThreadPoolExecutor.class);
        // 每3秒重置一次标志位，允许下一次invoke的执行
        scheduler.scheduleAtFixedRate(() -> isInvokeAllowed.set(true), 0, 3, TimeUnit.SECONDS);
        followTraderService=SpringContextUtils.getBean(FollowTraderServiceImpl.class);
    }


    public void invoke(Object sender, QuoteEventArgs quote) {
        if (isInvokeAllowed.compareAndSet(true, false)) {
            QuoteClient qc = (QuoteClient) sender;
            try {
                System.out.println("OnQuoteTraderHandler监听：" +abstractApiTrader.getTrader().getId());
                followTraderService.update((Wrappers.<FollowTraderEntity>lambdaUpdate().eq(FollowTraderEntity::getId,abstractApiTrader.getTrader().getId())
                        .set(FollowTraderEntity::getEuqit,qc.AccountEquity())
                        .set(FollowTraderEntity::getMarginProportion,BigDecimal.valueOf(qc.AccountMargin()).divide(BigDecimal.valueOf(qc.AccountEquity()),2, RoundingMode.HALF_UP))
                        .set(FollowTraderEntity::getBalance,qc.AccountBalance())));
            } catch (Exception e) {
                System.err.println("Error during quote processing: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
