package net.maku.mascontrol.even;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import net.maku.followcom.entity.FollowOrderSendEntity;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.followcom.service.FollowOrderSendService;
import net.maku.followcom.service.FollowSubscribeOrderService;
import net.maku.framework.common.cache.RedisCache;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.utils.JsonUtils;
import net.maku.framework.common.utils.ThreadPoolUtils;
import net.maku.mascontrol.trader.AbstractApiTrader;
import net.maku.followcom.util.SpringContextUtils;
import net.maku.mascontrol.vo.FollowOrderSendSocketVO;
import net.maku.mascontrol.vo.OrderActiveInfoVO;
import net.maku.mascontrol.websocket.TraderOrderSendWebSocket;
import online.mtapi.mt4.Order;
import online.mtapi.mt4.QuoteClient;
import online.mtapi.mt4.QuoteEventArgs;
import online.mtapi.mt4.QuoteEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static online.mtapi.mt4.Op.Buy;
import static online.mtapi.mt4.Op.Sell;

public class OnQuoteHandler implements QuoteEventHandler {
    private static final Logger log = LoggerFactory.getLogger(OnQuoteHandler.class);
    protected FollowTraderEntity leader;
    protected AbstractApiTrader abstractApiTrader;
    protected ScheduledThreadPoolExecutor scheduledThreadPoolExecutor;

    protected FollowSubscribeOrderService followSubscribeOrderService;

    protected Boolean running = Boolean.TRUE;

    private final AtomicBoolean isInvokeAllowed = new AtomicBoolean(true); // 使用AtomicBoolean，保证线程安全
    private TraderOrderSendWebSocket traderOrderSendWebSocket;
    private FollowOrderSendService followOrderSendService;
    private RedisCache redisCache;
    private final FollowOrderSendSocketVO followOrderSendSocketVO = new FollowOrderSendSocketVO();

    // 记录上次执行时间
    private long lastInvokeTime = 0;
    // 设定时间间隔，单位为毫秒
    private final long interval = 3000; // 3秒间隔

    public OnQuoteHandler(AbstractApiTrader abstractApiTrader ) {
        this.abstractApiTrader=abstractApiTrader;
        this.scheduledThreadPoolExecutor = ThreadPoolUtils.getScheduledExecute();
        this.followSubscribeOrderService = SpringContextUtils.getBean(FollowSubscribeOrderService.class);
        traderOrderSendWebSocket=SpringContextUtils.getBean(TraderOrderSendWebSocket.class);
        followOrderSendService=SpringContextUtils.getBean(FollowOrderSendService.class);
        redisCache=SpringContextUtils.getBean(RedisCache.class);
    }


    public void invoke(Object sender, QuoteEventArgs quote) {
        // 获取当前系统时间
        long currentTime = System.currentTimeMillis();
        // 判断当前时间与上次执行时间的间隔是否达到设定的间隔时间
        if (currentTime - lastInvokeTime >= interval) {
            // 更新上次执行时间为当前时间
            lastInvokeTime = currentTime;
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
        //所有持仓
        List<Order> openedOrders = Arrays.stream(qc.GetOpenedOrders()).filter(order -> order.Type == Buy || order.Type == Sell).collect(Collectors.toList());
        List<OrderActiveInfoVO> orderActiveInfoList=converOrderActive(openedOrders,qc.AccountName());

        //账户信息
        log.info("OnQuote监听：" +abstractApiTrader.getTrader().getId()+ quote.Symbol+quote.Bid+"dd"+quote.Ask);
        List<FollowOrderSendEntity> list;
        if (ObjectUtil.isEmpty(redisCache.get(Constant.TRADER_ORDER + abstractApiTrader.getTrader().getId()))){
            list = followOrderSendService.list(new LambdaQueryWrapper<FollowOrderSendEntity>().eq(FollowOrderSendEntity::getTraderId,abstractApiTrader.getTrader().getId()));
            redisCache.set(Constant.TRADER_ORDER + abstractApiTrader.getTrader().getId(),list);
        }else {
            list = (List<FollowOrderSendEntity>) redisCache.get(Constant.TRADER_ORDER + abstractApiTrader.getTrader().getId());
        }
        //查看当前账号订单完成进度
        followOrderSendSocketVO.setSellPrice(quote.Bid);
        followOrderSendSocketVO.setBuyPrice(quote.Ask);
        followOrderSendSocketVO.setStatus(CloseOrOpenEnum.OPEN.getValue());
        followOrderSendSocketVO.setOrderActiveInfoList(orderActiveInfoList);
        if (ObjectUtil.isNotEmpty(list)) {
            List<FollowOrderSendEntity> collect = list.stream().filter(o -> o.getStatus().equals(CloseOrOpenEnum.CLOSE.getValue())).collect(Collectors.toList());
            if (ObjectUtil.isNotEmpty(collect)) {
                //是否存在正在执行 进度
                FollowOrderSendEntity followOrderSendEntity = collect.get(0);
                followOrderSendSocketVO.setStatus(followOrderSendEntity.getStatus());
                followOrderSendSocketVO.setScheduleNum(followOrderSendEntity.getTotalNum());
                followOrderSendSocketVO.setScheduleSuccessNum(followOrderSendEntity.getSuccessNum());
                followOrderSendSocketVO.setScheduleFailNum(followOrderSendEntity.getFailNum());
            }
            collect.clear();
        }
        traderOrderSendWebSocket.pushMessage(abstractApiTrader.getTrader().getId().toString(),quote.Symbol, JsonUtils.toJsonString(followOrderSendSocketVO));
    }

    private List<OrderActiveInfoVO> converOrderActive(List<Order> openedOrders,String account) {
        return openedOrders.parallelStream().map(o->{
            OrderActiveInfoVO orderActiveInfoVO = new OrderActiveInfoVO();
            orderActiveInfoVO.setAccount(account);
            orderActiveInfoVO.setLots(o.Lots);
            orderActiveInfoVO.setComment(o.Comment);
            orderActiveInfoVO.setOrderNo(o.Ticket);
            orderActiveInfoVO.setCommission(o.Commission);
            orderActiveInfoVO.setSwap(o.Swap);
            orderActiveInfoVO.setProfit(o.Profit);
            orderActiveInfoVO.setSymbol(o.Symbol);
            orderActiveInfoVO.setOpenPrice(o.OpenPrice);
            orderActiveInfoVO.setMagicNumber(o.MagicNumber);
            orderActiveInfoVO.setType(o.Type.name());
            orderActiveInfoVO.setOpenTime(o.OpenTime);
            orderActiveInfoVO.setStopLoss(o.StopLoss);
            orderActiveInfoVO.setTakeProfit(o.TakeProfit);
            return orderActiveInfoVO;
        }).collect(Collectors.toList());
    }
}
