package net.maku.subcontrol.eventHandler;

import com.cld.message.pubsub.kafka.IKafkaProducer;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.service.FollowSubscribeOrderService;
import net.maku.subcontrol.trader.AbstractApiTrader;
import net.maku.subcontrol.util.SpringContextUtils;
import net.maku.subcontrol.util.ThreeStrategyThreadPoolExecutor;
import online.mtapi.mt4.QuoteClient;
import online.mtapi.mt4.QuoteEventArgs;
import online.mtapi.mt4.QuoteEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class OnQuoteHandler implements QuoteEventHandler {
    private static final Logger log = LoggerFactory.getLogger(OnQuoteHandler.class);
    protected FollowTraderEntity leader;
    protected AbstractApiTrader abstractApiTrader;
    protected ScheduledThreadPoolExecutor scheduledThreadPoolExecutor;
    protected IKafkaProducer<String, Object> kafkaProducer;
    protected ThreeStrategyThreadPoolExecutor analysisExecutorService;

    protected FollowSubscribeOrderService followSubscribeOrderService;

    protected Boolean running = Boolean.TRUE;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final AtomicBoolean isInvokeAllowed = new AtomicBoolean(true); // 使用AtomicBoolean，保证线程安全


    public OnQuoteHandler( AbstractApiTrader abstractApiTrader ) {
        this.abstractApiTrader=abstractApiTrader;
        this.scheduledThreadPoolExecutor = SpringContextUtils.getBean("scheduledExecutorService", ScheduledThreadPoolExecutor.class);
        this.analysisExecutorService = SpringContextUtils.getBean("analysisExecutorService", ThreeStrategyThreadPoolExecutor.class);
        this.followSubscribeOrderService = SpringContextUtils.getBean(FollowSubscribeOrderService.class);
        // 每3秒重置一次标志位，允许下一次invoke的执行
        scheduler.scheduleAtFixedRate(() -> isInvokeAllowed.set(true), 0, 3, TimeUnit.SECONDS);
    }


    public void invoke(Object sender, QuoteEventArgs quote) {
        if (isInvokeAllowed.compareAndSet(true, false)) {
            log.info("时间"+new Date());
            QuoteClient qc = (QuoteClient) sender;
            try {
                handleQuote(qc, quote);
            } catch (Exception e) {
                System.err.println("Error during quote processing: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void handleQuote(QuoteClient qc, QuoteEventArgs quote) {
        //账户信息更新
        //账户持仓订单更新
        System.out.println("OnQuote监听：" + qc.Equity+"dd"+qc.GetOpenedOrders().length);
        for (int i = 0; i < qc.GetOpenedOrders().length; i++) {
            System.out.println("订单:" + qc.GetOpenedOrders()[i].Ticket + "profit" + qc.GetOpenedOrders()[i].Profit);
        }
    }
}
