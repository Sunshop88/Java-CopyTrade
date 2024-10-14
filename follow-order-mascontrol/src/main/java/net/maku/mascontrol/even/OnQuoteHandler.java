package net.maku.mascontrol.even;

import cn.hutool.core.date.DateUtil;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static online.mtapi.mt4.Op.Buy;
import static online.mtapi.mt4.Op.Sell;

public class OnQuoteHandler implements QuoteEventHandler {
    private static final Logger log = LoggerFactory.getLogger(OnQuoteHandler.class);
    protected FollowTraderEntity leader;
    protected AbstractApiTrader abstractApiTrader;

    protected FollowSubscribeOrderService followSubscribeOrderService;

    protected Boolean running = Boolean.TRUE;

    private TraderOrderSendWebSocket traderOrderSendWebSocket;
    private FollowOrderSendService followOrderSendService;
    private RedisCache redisCache;
    private final FollowOrderSendSocketVO followOrderSendSocketVO = new FollowOrderSendSocketVO();

    // 用于存储每个Symbol的锁
    private static final ConcurrentHashMap<String, Lock> symbolLockMap = new ConcurrentHashMap<>();
    private final OrderActiveInfoVOPool orderActiveInfoVOPool = new OrderActiveInfoVOPool();

    // 设定时间间隔，单位为毫秒
    private final long interval = 3000; // 3秒间隔
    // 用于存储每个 symbol 上次执行时间
    private static final ConcurrentHashMap<String, Long> symbolLastInvokeTimeMap = new ConcurrentHashMap<>();

    public OnQuoteHandler(AbstractApiTrader abstractApiTrader ) {
        this.abstractApiTrader=abstractApiTrader;
        this.followSubscribeOrderService = SpringContextUtils.getBean(FollowSubscribeOrderService.class);
        traderOrderSendWebSocket=SpringContextUtils.getBean(TraderOrderSendWebSocket.class);
        followOrderSendService=SpringContextUtils.getBean(FollowOrderSendService.class);
        redisCache=SpringContextUtils.getBean(RedisCache.class);
    }


    public void invoke(Object sender, QuoteEventArgs quote) {

        // 判断当前时间与上次执行时间的间隔是否达到设定的间隔时间

        // 获取当前系统时间
        long currentTime = System.currentTimeMillis();
        // 获取该symbol上次执行时间
        long lastSymbolInvokeTime = symbolLastInvokeTimeMap.getOrDefault(quote.Symbol, 0L);
        if (currentTime - lastSymbolInvokeTime  >= interval) {
            // 更新该symbol的上次执行时间为当前时间
            symbolLastInvokeTimeMap.put(quote.Symbol, currentTime);
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
        openedOrders=null;
        orderActiveInfoList=null;
        traderOrderSendWebSocket.pushMessage(abstractApiTrader.getTrader().getId().toString(),quote.Symbol, JsonUtils.toJsonString(followOrderSendSocketVO));
    }

    private List<OrderActiveInfoVO> converOrderActive(List<Order> openedOrders, String account) {
        List<OrderActiveInfoVO> collect = openedOrders.stream().map(o -> {
            OrderActiveInfoVO reusableOrderActiveInfoVO = orderActiveInfoVOPool.borrowObject();
            resetOrderActiveInfoVO(reusableOrderActiveInfoVO, o, account);  // 重用并重置对象
            return reusableOrderActiveInfoVO;
        }).collect(Collectors.toList());
        // 将对象归还到池中（假设在调用完毕后做这个操作
        ThreadPoolUtils.execute(()->{
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            collect.forEach(orderActiveInfoVOPool::returnObject);
        });
        return collect;
    }

    private void resetOrderActiveInfoVO(OrderActiveInfoVO vo, Order order, String account) {
        vo.setAccount(account);
        vo.setLots(order.Lots);
        vo.setComment(order.Comment);
        vo.setOrderNo(order.Ticket);
        vo.setCommission(order.Commission);
        vo.setSwap(order.Swap);
        vo.setProfit(order.Profit);
        vo.setSymbol(order.Symbol);
        vo.setOpenPrice(order.OpenPrice);
        vo.setMagicNumber(order.MagicNumber);
        vo.setType(order.Type.name());
        //增加五小时
        vo.setOpenTime(DateUtil.toLocalDateTime(DateUtil.offsetHour(DateUtil.date(order.OpenTime),5)));
        vo.setStopLoss(order.StopLoss);
        vo.setTakeProfit(order.TakeProfit);
    }

    private Lock getLock(String symbol) {
        // 如果没有锁对象，使用ReentrantLock创建一个新的锁
        return symbolLockMap.computeIfAbsent(symbol, k -> new ReentrantLock());
    }
}
