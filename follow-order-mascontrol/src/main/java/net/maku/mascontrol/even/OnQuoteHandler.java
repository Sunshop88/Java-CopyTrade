package net.maku.mascontrol.even;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cld.message.pubsub.kafka.IKafkaProducer;
import net.maku.followcom.entity.FollowOrderSendEntity;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.followcom.service.FollowOrderSendService;
import net.maku.followcom.service.FollowSubscribeOrderService;
import net.maku.framework.common.utils.JsonUtils;
import net.maku.mascontrol.trader.AbstractApiTrader;
import net.maku.mascontrol.util.SpringContextUtils;
import net.maku.mascontrol.vo.FollowOrderSendSocketVO;
import net.maku.mascontrol.websocket.TraderOrderSendWebSocket;
import online.mtapi.mt4.QuoteClient;
import online.mtapi.mt4.QuoteEventArgs;
import online.mtapi.mt4.QuoteEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class OnQuoteHandler implements QuoteEventHandler {
    private static final Logger log = LoggerFactory.getLogger(OnQuoteHandler.class);
    protected FollowTraderEntity leader;
    protected AbstractApiTrader abstractApiTrader;
    protected ScheduledThreadPoolExecutor scheduledThreadPoolExecutor;

    protected FollowSubscribeOrderService followSubscribeOrderService;

    protected Boolean running = Boolean.TRUE;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final AtomicBoolean isInvokeAllowed = new AtomicBoolean(true); // 使用AtomicBoolean，保证线程安全
    private TraderOrderSendWebSocket traderOrderSendWebSocket;
    private FollowOrderSendService followOrderSendService;

    public OnQuoteHandler(AbstractApiTrader abstractApiTrader ) {
        this.abstractApiTrader=abstractApiTrader;
        this.scheduledThreadPoolExecutor = SpringContextUtils.getBean("scheduledExecutorService", ScheduledThreadPoolExecutor.class);
        this.followSubscribeOrderService = SpringContextUtils.getBean(FollowSubscribeOrderService.class);
        // 每3秒重置一次标志位，允许下一次invoke的执行
        scheduler.scheduleAtFixedRate(() -> isInvokeAllowed.set(true), 0, 3, TimeUnit.SECONDS);
        traderOrderSendWebSocket=SpringContextUtils.getBean(TraderOrderSendWebSocket.class);
        followOrderSendService=SpringContextUtils.getBean(FollowOrderSendService.class);
    }


    public void invoke(Object sender, QuoteEventArgs quote) {
        if (isInvokeAllowed.compareAndSet(true, false)) {
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
        //账户信息
        System.out.println("OnQuote监听：" +abstractApiTrader.getTrader().getId()+ quote.Symbol+quote.Bid+"dd"+quote.Ask);
        //查看当前账号订单完成进度
        List<FollowOrderSendEntity> list = followOrderSendService.list(new LambdaQueryWrapper<FollowOrderSendEntity>().eq(FollowOrderSendEntity::getTraderId,abstractApiTrader.getTrader().getId()));
        FollowOrderSendSocketVO followOrderSendSocketVO=new FollowOrderSendSocketVO();
        followOrderSendSocketVO.setSellPrice(quote.Bid);
        followOrderSendSocketVO.setBuyPrice(quote.Ask);
        followOrderSendSocketVO.setStatus(CloseOrOpenEnum.OPEN.getValue());
        List<FollowOrderSendEntity> collect = list.stream().filter(o -> o.getStatus().equals(CloseOrOpenEnum.CLOSE.getValue())).collect(Collectors.toList());
        if (ObjectUtil.isNotEmpty(collect)){
            //是否存在正在执行 进度
            FollowOrderSendEntity followOrderSendEntity = collect.get(0);
            followOrderSendSocketVO.setStatus(followOrderSendEntity.getStatus());
            followOrderSendSocketVO.setScheduleNum(followOrderSendEntity.getTotalNum());
            followOrderSendSocketVO.setScheduleSuccessNum(followOrderSendEntity.getSuccessNum());
        }
        //总数量
        followOrderSendSocketVO.setFailNum(list.stream().mapToInt(FollowOrderSendEntity::getFailNum).sum());
        followOrderSendSocketVO.setSuccessNum(list.stream().mapToInt(FollowOrderSendEntity::getSuccessNum).sum());
        followOrderSendSocketVO.setTotalNum(list.stream().mapToInt(FollowOrderSendEntity::getTotalNum).sum());
        traderOrderSendWebSocket.pushMessage(abstractApiTrader.getTrader().getId().toString(),quote.Symbol, JsonUtils.toJsonString(followOrderSendSocketVO));
    }
}
